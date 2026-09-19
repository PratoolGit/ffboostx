package com.ffboostx.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** One probe attempt. A null [rttMs] means the attempt timed out or was refused. */
data class Probe(val rttMs: Double?)

data class NetworkQuality(
    val target: String,
    val probes: List<Probe>,
    val avgMs: Double?,
    val minMs: Double?,
    val maxMs: Double?,
    /** Mean absolute difference between consecutive successful probes. */
    val jitterMs: Double?,
    val lossPercent: Double,
    val transport: String,
    /** Android's own estimate of the link, in kbps. Not a speed test. */
    val downstreamKbps: Int?,
    val validated: Boolean
) {
    /**
     * A 0-100 summary of how *steady* the link is. It is deliberately built from
     * jitter and loss only - not from absolute latency, which the phone cannot
     * influence. The formula is shown in the UI so it is not a magic number.
     */
    val stabilityPercent: Int
        get() {
            if (avgMs == null) return 0
            val jitterPenalty = ((jitterMs ?: 0.0) * 2.5).coerceAtMost(60.0)
            val lossPenalty = (lossPercent * 4.0).coerceAtMost(60.0)
            return (100.0 - jitterPenalty - lossPenalty).coerceIn(0.0, 100.0).toInt()
        }

    val rating: String
        get() = when {
            avgMs == null -> "No route"
            stabilityPercent >= 90 -> "Stable"
            stabilityPercent >= 70 -> "Usable"
            stabilityPercent >= 45 -> "Unsteady"
            else -> "Poor"
        }
}

data class DnsResult(
    val name: String,
    val address: String,
    val latencyMs: Double?,
    val note: String? = null
)

/**
 * Network diagnostics built only from sockets an ordinary app is allowed to open.
 *
 * Two honest limitations are surfaced everywhere these numbers appear:
 *  - ICMP ping needs a raw socket, which apps cannot create, so latency is
 *    measured as a TCP handshake round trip instead. It tracks ping closely but
 *    is not the same measurement.
 *  - None of this changes latency. It measures it.
 */
class NetworkEngine(context: Context) {

    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
        as ConnectivityManager

    /**
     * Opens and closes [count] TCP connections to [host]:[port], timing each one.
     * Runs on the IO dispatcher; never call this from the main thread.
     */
    suspend fun measureQuality(
        host: String = DEFAULT_HOST,
        port: Int = 53,
        count: Int = 8,
        timeoutMs: Int = 1_500
    ): NetworkQuality = withContext(Dispatchers.IO) {
        val address = runCatching { InetAddress.getByName(host) }.getOrNull()
        val probes = if (address == null) {
            List(count) { Probe(null) }
        } else {
            List(count) {
                Probe(tcpHandshakeMs(address, port, timeoutMs))
            }
        }

        val successes = probes.mapNotNull { it.rttMs }
        val jitter = if (successes.size >= 2) {
            successes.zipWithNext { a, b -> abs(b - a) }.average()
        } else {
            null
        }

        NetworkQuality(
            target = host,
            probes = probes,
            avgMs = successes.takeIf { it.isNotEmpty() }?.average(),
            minMs = successes.minOrNull(),
            maxMs = successes.maxOrNull(),
            jitterMs = jitter,
            lossPercent = (probes.size - successes.size) * 100.0 / max(1, probes.size),
            transport = transportLabel(),
            downstreamKbps = downstreamKbps(),
            validated = isValidated()
        )
    }

    private fun tcpHandshakeMs(address: InetAddress, port: Int, timeoutMs: Int): Double? =
        runCatching {
            Socket().use { socket ->
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(address, port), timeoutMs)
                (System.nanoTime() - start) / 1_000_000.0
            }
        }.getOrNull()

    /**
     * Times a real DNS query against each resolver over UDP. A random label is
     * prefixed to the query name so no resolver can answer from cache and make
     * itself look artificially fast.
     */
    suspend fun measureDns(timeoutMs: Int = 1_500): List<DnsResult> =
        withContext(Dispatchers.IO) {
            val probeName = "${Random.nextInt(100_000, 999_999)}.ffboostx-probe.example"

            val system = DnsResult(
                name = "System resolver",
                address = "current",
                latencyMs = runCatching {
                    val start = System.nanoTime()
                    // Expected to fail with UnknownHost - the timing is the point.
                    runCatching { InetAddress.getByName(probeName) }
                    (System.nanoTime() - start) / 1_000_000.0
                }.getOrNull(),
                note = "Whatever your network or Private DNS setting currently uses."
            )

            val others = PUBLIC_RESOLVERS.map { (label, ip) ->
                DnsResult(
                    name = label,
                    address = ip,
                    latencyMs = udpDnsQueryMs(ip, probeName, timeoutMs)
                )
            }

            listOf(system) + others
        }

    /**
     * Minimal DNS/UDP client: builds a type-A query by hand, sends it and times
     * the reply. Only the round trip is used; the answer is discarded.
     */
    private fun udpDnsQueryMs(resolver: String, name: String, timeoutMs: Int): Double? =
        runCatching {
            val query = buildDnsQuery(name)
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val server = InetAddress.getByName(resolver)
                val out = DatagramPacket(query, query.size, server, 53)
                val buffer = ByteArray(512)
                val reply = DatagramPacket(buffer, buffer.size)

                val start = System.nanoTime()
                socket.send(out)
                socket.receive(reply)
                (System.nanoTime() - start) / 1_000_000.0
            }
        }.getOrNull()

    private fun buildDnsQuery(name: String): ByteArray {
        val labels = name.split(".").filter { it.isNotEmpty() }
        val body = ArrayList<Byte>(64)

        val id = Random.nextInt(0, 0xFFFF)
        body.add((id shr 8).toByte())
        body.add((id and 0xFF).toByte())
        body.add(0x01.toByte()); body.add(0x00.toByte())  // standard query, recursion desired
        body.add(0x00.toByte()); body.add(0x01.toByte())  // one question
        body.add(0x00.toByte()); body.add(0x00.toByte())  // no answers
        body.add(0x00.toByte()); body.add(0x00.toByte())  // no authority records
        body.add(0x00.toByte()); body.add(0x00.toByte())  // no additional records

        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            body.add(bytes.size.toByte())
            bytes.forEach { body.add(it) }
        }
        body.add(0x00.toByte())                           // end of name
        body.add(0x00.toByte()); body.add(0x01.toByte())  // QTYPE = A
        body.add(0x00.toByte()); body.add(0x01.toByte())  // QCLASS = IN

        return body.toByteArray()
    }

    // ------------------------------------------------------------- link info

    fun transportLabel(): String = runCatching {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return "Offline")
            ?: return "Unknown"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }
    }.getOrDefault("Unknown")

    private fun downstreamKbps(): Int? = runCatching {
        cm.getNetworkCapabilities(cm.activeNetwork ?: return null)
            ?.linkDownstreamBandwidthKbps
            ?.takeIf { it > 0 }
    }.getOrNull()

    private fun isValidated(): Boolean = runCatching {
        cm.getNetworkCapabilities(cm.activeNetwork ?: return false)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrDefault(false)

    /**
     * Emits whenever the active network changes. Cold, so the callback is
     * registered on collection and unregistered the moment collection stops -
     * there is no receiver left behind when the app is backgrounded.
     */
    fun transportChanges(): Flow<String> = callbackFlow {
        trySend(transportLabel())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(transportLabel())
            }

            override fun onLost(network: Network) {
                trySend(transportLabel())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities
            ) {
                trySend(transportLabel())
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
        awaitClose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }

    companion object {
        /**
         * A public resolver on port 53 is used as the latency target because it
         * answers TCP handshakes reliably worldwide. It is not a game server, and
         * the UI says so: the figure is a route-quality indicator, not your
         * in-match ping.
         */
        const val DEFAULT_HOST = "8.8.8.8"

        val PUBLIC_RESOLVERS = listOf(
            "Cloudflare" to "1.1.1.1",
            "Google" to "8.8.8.8",
            "Quad9" to "9.9.9.9"
        )

        @Volatile
        private var instance: NetworkEngine? = null

        fun get(context: Context): NetworkEngine =
            instance ?: synchronized(this) {
                instance ?: NetworkEngine(context).also { instance = it }
            }
    }
}
