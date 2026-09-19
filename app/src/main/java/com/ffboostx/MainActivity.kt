package com.ffboostx

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ffboostx.core.BoostEngine
import com.ffboostx.core.BoostPhase
import com.ffboostx.core.BoostProgress
import com.ffboostx.core.CapabilityRegistry
import com.ffboostx.core.DeviceMonitor
import com.ffboostx.core.DnsResult
import com.ffboostx.core.FrameMeter
import com.ffboostx.core.GameProfile
import com.ffboostx.core.GameProfileStore
import com.ffboostx.core.LiveSession
import com.ffboostx.core.MotionBudget
import com.ffboostx.core.MacroStore
import com.ffboostx.core.NetworkEngine
import com.ffboostx.core.NetworkQuality
import com.ffboostx.core.OverlayConfigStore
import com.ffboostx.core.PerfEngine
import com.ffboostx.core.PerformanceProfile
import com.ffboostx.core.SessionStore
import com.ffboostx.core.SettingsStore
import com.ffboostx.core.StepOutcome
import com.ffboostx.core.SystemActions
import com.ffboostx.core.ThermalEngine
import com.ffboostx.core.ThermalLevel
import com.ffboostx.core.ThermalProfile
import com.ffboostx.core.WindowController
import com.ffboostx.core.valueOrNull
import com.ffboostx.overlay.BackgroundNetworkBlocker
import com.ffboostx.overlay.GamingOverlayService
import com.ffboostx.ui.boost.BoostPopup
import com.ffboostx.ui.components.DialogButton
import com.ffboostx.ui.components.PermissionDialog
import com.ffboostx.ui.components.PremiumDialog
import com.ffboostx.ui.screens.BoostScreen
import com.ffboostx.ui.screens.CapabilitiesScreen
import com.ffboostx.ui.screens.GameScreen
import com.ffboostx.ui.screens.HistoryScreen
import com.ffboostx.ui.screens.HomeScreen
import com.ffboostx.ui.screens.NetworkScreen
import com.ffboostx.ui.screens.MacroScreen
import com.ffboostx.ui.screens.OverlayScreen
import com.ffboostx.ui.screens.PerformanceScreen
import com.ffboostx.ui.screens.SettingsScreen
import com.ffboostx.ui.screens.ThermalScreen
import com.ffboostx.ui.theme.BoostColors
import com.ffboostx.ui.theme.FFBoostXTheme
import com.ffboostx.ui.theme.GamingBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The only Activity-bound controls the engine needs. Passing an
        // interface instead of the Activity keeps every long-lived object in
        // core/ free of an Activity reference.
        val controller = object : WindowController {
            override fun requestRefreshRate(targetHz: Int?): Boolean =
                SystemActions.get(this@MainActivity)
                    .requestRefreshRate(this@MainActivity, targetHz)

            override fun setKeepScreenOn(keepOn: Boolean) {
                SystemActions.get(this@MainActivity).setKeepScreenOn(window, keepOn)
            }

            override fun setSustainedPerformance(enabled: Boolean): Boolean =
                SystemActions.get(this@MainActivity).setSustainedPerformance(window, enabled)
        }

        setContent { BoostXApp(controller) }
    }
}

private enum class Tab(val label: String) {
    HOME("Home"),
    BOOST("Boost"),
    PERFORMANCE("Performance"),
    NETWORK("Network"),
    THERMAL("Thermal"),
    OVERLAY("Overlay"),
    GAME("Game"),
    HISTORY("History"),
    CAPABILITIES("Capabilities"),
    SETTINGS("Settings"),
    MACROS("Macros")
}

private enum class Prompt { NONE, DND, WRITE_SETTINGS, OVERLAY, USAGE }

@Composable
private fun BoostXApp(controller: WindowController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settingsStore = remember { SettingsStore.get(context) }
    val overlayStore = remember { OverlayConfigStore.get(context) }
    val profileStore = remember { GameProfileStore.get(context) }
    val sessions = remember { SessionStore.get(context) }
    val macroStore = remember { MacroStore.get(context) }
    val monitor = remember { DeviceMonitor.get(context) }
    val actions = remember { SystemActions.get(context) }
    val network = remember { NetworkEngine.get(context) }
    val thermal = remember { ThermalEngine.get(context) }
    val frameMeter = remember { FrameMeter() }
    val detectedProfile = remember { PerformanceProfile.detect(context) }
    val engine = remember {
        BoostEngine(monitor, actions, settingsStore, network, thermal, overlayStore)
    }

    val settings by settingsStore.state.collectAsStateWithLifecycle()
    val overlayConfig by overlayStore.state.collectAsStateWithLifecycle()
    val profiles by profileStore.profiles.collectAsStateWithLifecycle()
    val activeProfileId by profileStore.activeId.collectAsStateWithLifecycle()
    val history by sessions.history.collectAsStateWithLifecycle()
    val liveSession by sessions.live.collectAsStateWithLifecycle()
    val blockerActive by BackgroundNetworkBlocker.active.collectAsStateWithLifecycle()
    var macroRevision by remember { mutableIntStateOf(0) }

    val profile = when {
        settings.lowRamMode -> PerformanceProfile.LOW
        else -> settings.profileOverride ?: detectedProfile
    }
    val motion = MotionBudget(profile, settings.animationIntensity)
    val intervalMs = settings.refreshRate.intervalMs ?: profile.statsIntervalMs

    val snapshot by remember(intervalMs) { monitor.snapshots(intervalMs) }
        .collectAsStateWithLifecycle(initialValue = null)
    val thermalLevel by remember { thermal.statusChanges() }
        .collectAsStateWithLifecycle(initialValue = ThermalLevel.NONE)
    val transport by remember { network.transportChanges() }
        .collectAsStateWithLifecycle(initialValue = "Unknown")

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    // Frame measurement is only collected while the Performance tab is open, so
    // the vsync callback is not running the rest of the time.
    val appFps: Float? by remember(tab) {
        if (tab == Tab.PERFORMANCE) frameMeter.rates() else emptyFlow()
    }.collectAsStateWithLifecycle(initialValue = null)

    var resumeKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val installedGames = remember(resumeKey) { actions.installedGames() }
    LaunchedEffect(installedGames) { profileStore.seed(installedGames) }

    val capabilities = remember(resumeKey) { CapabilityRegistry.build(context, actions) }
    val overlayPermitted = remember(resumeKey) { actions.canDrawOverlays() }
    val usageGranted = remember(resumeKey) { actions.hasUsageAccess() }
    val canWriteSettings = remember(resumeKey) { actions.canWriteSystemSettings() }
    val refreshRates = remember { actions.supportedRefreshRates() }
    val sustainedSupported = remember { PerfEngine.sustainedPerformanceSupported(context) }
    val hintFrameworkPresent = remember { PerfEngine.hintManagerAvailable(context) }
    val gameManagerPresent = remember { PerfEngine.gameManagerAvailable(context) }
    val dndGranted = remember(resumeKey) { actions.hasDndAccess() }

    val activeProfile = profiles.firstOrNull { it.id == activeProfileId }
    val overlayRunning = overlayConfig.hudEnabled || overlayConfig.crosshairEnabled

    val advice = remember(thermalLevel, snapshot, settings.gamingModeOn, activeProfile) {
        thermal.advise(
            level = thermalLevel,
            batteryTempC = snapshot?.battery?.temperatureC?.valueOrNull(),
            profile = activeProfile?.thermalProfile ?: ThermalProfile.BALANCED,
            charging = snapshot?.battery?.charging == true
        )
    }

    val headroom = remember(thermalLevel, resumeKey) { thermal.headroom() }

    LaunchedEffect(thermalLevel) { sessions.recordThermal(thermalLevel) }

    // Auto-start, applied once per launch.
    LaunchedEffect(Unit) {
        if (settingsStore.current.autoStartGamingMode && !settingsStore.current.gamingModeOn) {
            settingsStore.update { it.copy(gamingModeOn = true) }
        }
    }

    var boostProgress by remember { mutableStateOf<BoostProgress?>(null) }
    var lastResult by remember { mutableStateOf<BoostProgress?>(null) }
    var quality by remember { mutableStateOf<NetworkQuality?>(null) }
    var testing by remember { mutableStateOf(false) }
    var dnsResults by remember { mutableStateOf<List<DnsResult>>(emptyList()) }
    var dnsTesting by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf(Prompt.NONE) }
    var showLicences by remember { mutableStateOf(false) }
    var sessionTick by remember { mutableLongStateOf(0L) }

    // Android asks for VPN consent through an Activity result, so the launcher
    // lives here and the service is only started once consent comes back.
    val vpnConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            BackgroundNetworkBlocker.start(context, activeProfile?.packageName)
        }
    }

    fun toggleBlocker(on: Boolean) {
        if (!on) {
            BackgroundNetworkBlocker.stop(context)
            return
        }
        val consent = BackgroundNetworkBlocker.consentIntent(context)
        if (consent != null) {
            vpnConsent.launch(consent)
        } else {
            BackgroundNetworkBlocker.start(context, activeProfile?.packageName)
        }
    }

    fun startOverlayService(): Boolean {
        if (!actions.canDrawOverlays()) return false
        GamingOverlayService.start(context, activeProfile?.packageName)
        return true
    }

    val runBoost: () -> Unit = {
        if (boostProgress == null) {
            scope.launch {
                engine.run(
                    profile = activeProfile,
                    window = controller,
                    motion = motion,
                    onOverlayRequested = { startOverlayService() }
                ).collect { progress ->
                    boostProgress = progress
                    if (progress.phase == BoostPhase.DONE) {
                        lastResult = progress
                        progress.quality?.let { quality = it }

                        // A session only opens when a game is actually ready to
                        // launch, so the history does not fill with no-ops.
                        if (progress.launchPackage != null) {
                            val memory = progress.after?.freeMemoryBytes
                            sessions.start(
                                LiveSession(
                                    gameName = activeProfile?.name ?: "Session",
                                    startedAtEpochMs = System.currentTimeMillis(),
                                    startedAtElapsedMs = SystemClock.elapsedRealtime(),
                                    tempStartC = progress.after?.batteryTempC,
                                    batteryStartPercent = progress.after?.batteryPercent,
                                    freeMemoryStartBytes = memory,
                                    appliedCount = progress.count(StepOutcome.APPLIED),
                                    limitedCount = progress.count(StepOutcome.LIMITED),
                                    unavailableCount = progress.count(StepOutcome.UNAVAILABLE) +
                                        progress.count(StepOutcome.SKIPPED),
                                    failedCount = progress.count(StepOutcome.FAILED)
                                )
                            )
                            if (activeProfile?.blockBackgroundNetwork == true) {
                                toggleBlocker(true)
                            }
                        }
                    }
                }
            }
        }
    }

    fun endSession() {
        val snapshotNow = monitor.reader.read()
        sessions.finish(
            elapsedNowMs = SystemClock.elapsedRealtime(),
            tempEndC = snapshotNow.battery.temperatureC.valueOrNull(),
            batteryEndPercent = snapshotNow.battery.percent,
            freeMemoryEndBytes = snapshotNow.memory.availableBytes
        )
        // Everything the session turned on comes back off together.
        GamingOverlayService.stop(context)
        BackgroundNetworkBlocker.stop(context)
        actions.revertAll(settingsStore)
        controller.setKeepScreenOn(false)
        settingsStore.update { it.copy(gamingModeOn = false) }
        tab = Tab.HISTORY
    }

    // Drives the "minutes so far" counter without a timer when no session is up.
    LaunchedEffect(liveSession != null) {
        while (liveSession != null) {
            sessionTick = SystemClock.elapsedRealtime()
            delay(30_000L)
        }
    }

    val sessionMinutes = liveSession?.let { session ->
        // sessionTick is refreshed every 30s by the effect above; reading it here
        // is what makes this recompose without a per-frame timer.
        val now = if (sessionTick > 0L) sessionTick else SystemClock.elapsedRealtime()
        ((now - session.startedAtElapsedMs) / 60_000L).toInt().coerceAtLeast(0)
    }

    FFBoostXTheme(motion = motion) {
        GamingBackground(style = settings.backgroundStyle) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    TabStrip(selected = tab, onSelect = { tab = it })
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp)
                        .padding(top = 8.dp, bottom = 24.dp)
                ) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            if (motion.transitionsEnabled) {
                                (
                                    fadeIn(tween(motion.durationMs(180))) +
                                        slideInVertically(
                                            tween(motion.durationMs(200))
                                        ) { full -> full / 24 }
                                    ).togetherWith(fadeOut(tween(motion.durationMs(110))))
                            } else {
                                fadeIn(tween(1)).togetherWith(fadeOut(tween(1)))
                            }
                        },
                        label = "tabTransition"
                    ) { current ->
                        when (current) {
                            Tab.HOME -> HomeScreen(
                                snapshot = snapshot,
                                profile = profile,
                                thermalLevel = thermalLevel,
                                advice = advice,
                                quality = quality,
                                transport = transport,
                                gamingModeOn = settings.gamingModeOn,
                                overlayRunning = overlayRunning,
                                blockerActive = blockerActive,
                                sessionMinutes = sessionMinutes,
                                boosting = boostProgress?.phase == BoostPhase.RUNNING,
                                onBoost = runBoost
                            )

                            Tab.BOOST -> BoostScreen(
                                lastResult = lastResult,
                                boosting = boostProgress?.phase == BoostPhase.RUNNING,
                                gamingModeOn = settings.gamingModeOn,
                                profileName = activeProfile?.name,
                                pendingChanges = actions.hasPendingChanges(settings),
                                dndGranted = dndGranted,
                                writeSettingsGranted = canWriteSettings,
                                overlayGranted = overlayPermitted,
                                onBoost = runBoost,
                                onRestore = {
                                    actions.revertAll(settingsStore)
                                    controller.setKeepScreenOn(false)
                                },
                                onRequestDnd = { prompt = Prompt.DND },
                                onRequestWriteSettings = { prompt = Prompt.WRITE_SETTINGS },
                                onRequestOverlay = { prompt = Prompt.OVERLAY }
                            )

                            Tab.PERFORMANCE -> PerformanceScreen(
                                snapshot = snapshot,
                                appRenderFps = appFps,
                                profile = profile,
                                detectedProfile = detectedProfile,
                                refreshRate = settings.refreshRate,
                                intervalMs = intervalMs,
                                sustainedSupported = sustainedSupported,
                                hintFrameworkPresent = hintFrameworkPresent,
                                gameManagerPresent = gameManagerPresent,
                                onSelectProfile = { chosen ->
                                    settingsStore.update { it.copy(profileOverride = chosen) }
                                },
                                onSelectRefreshRate = { rate ->
                                    settingsStore.update { it.copy(refreshRate = rate) }
                                },
                                onOpenDeveloperOptions = {
                                    actions.start(context, actions.developerOptionsIntent())
                                }
                            )

                            Tab.NETWORK -> NetworkScreen(
                                quality = quality,
                                testing = testing,
                                dnsResults = dnsResults,
                                dnsTesting = dnsTesting,
                                transport = transport,
                                blockerActive = blockerActive,
                                blockerAvailable = true,
                                onRunTest = {
                                    if (!testing) {
                                        testing = true
                                        scope.launch {
                                            quality = runCatching { network.measureQuality() }
                                                .getOrNull()
                                            quality?.let { sessions.recordNetworkSample(it) }
                                            testing = false
                                        }
                                    }
                                },
                                onRunDnsTest = {
                                    if (!dnsTesting) {
                                        dnsTesting = true
                                        scope.launch {
                                            dnsResults = runCatching { network.measureDns() }
                                                .getOrDefault(emptyList())
                                            dnsTesting = false
                                        }
                                    }
                                },
                                onToggleBlocker = { on -> toggleBlocker(on) },
                                onOpenPrivateDns = {
                                    actions.start(context, actions.privateDnsIntent())
                                },
                                onOpenDataUsage = {
                                    actions.start(context, actions.dataUsageIntent())
                                }
                            )

                            Tab.THERMAL -> ThermalScreen(
                                level = thermalLevel,
                                batteryTempC = snapshot?.battery?.temperatureC?.valueOrNull(),
                                headroom = headroom,
                                advice = advice,
                                profile = activeProfile?.thermalProfile ?: ThermalProfile.BALANCED,
                                charging = snapshot?.battery?.charging == true,
                                onSelectProfile = { chosen ->
                                    activeProfile?.let {
                                        profileStore.save(it.copy(thermalProfile = chosen))
                                    }
                                }
                            )

                            Tab.OVERLAY -> OverlayScreen(
                                config = overlayConfig,
                                overlayPermitted = overlayPermitted,
                                usageAccessGranted = usageGranted,
                                serviceRunning = overlayRunning,
                                onUpdate = { transform -> overlayStore.update(transform) },
                                onRequestOverlayPermission = { prompt = Prompt.OVERLAY },
                                onRequestUsageAccess = { prompt = Prompt.USAGE },
                                onStartOverlay = { startOverlayService() },
                                onStopOverlay = { GamingOverlayService.stop(context) }
                            )

                            Tab.GAME -> GameScreen(
                                profiles = profiles,
                                activeProfile = activeProfile,
                                installedGames = installedGames,
                                supportedRefreshRates = refreshRates,
                                canWriteSettings = canWriteSettings,
                                onSelectProfile = { p: GameProfile ->
                                    profileStore.setActive(p.id)
                                },
                                onSaveProfile = { p: GameProfile -> profileStore.save(p) },
                                onResetProfile = { p: GameProfile -> profileStore.reset(p.id) },
                                onDeleteProfile = { p: GameProfile -> profileStore.delete(p.id) },
                                onBoostAndLaunch = runBoost,
                                onRequestWriteSettings = { prompt = Prompt.WRITE_SETTINGS }
                            )

                            Tab.HISTORY -> HistoryScreen(
                                live = liveSession,
                                liveMinutes = sessionMinutes ?: 0,
                                history = history,
                                onEndSession = { endSession() },
                                onClearHistory = { sessions.clearHistory() }
                            )

                            Tab.CAPABILITIES -> CapabilitiesScreen(capabilities = capabilities)

                            Tab.MACROS -> MacroScreen(
                                context = context,
                                macros = remember(macroRevision) { macroStore.all() },
                                onRefresh = { macroRevision++ }
                            )

                            Tab.SETTINGS -> SettingsScreen(
                                settings = settings,
                                detectedProfile = detectedProfile,
                                effectiveIntervalMs = intervalMs,
                                appVersion = BuildConfig.VERSION_NAME,
                                onUpdate = { transform -> settingsStore.update(transform) },
                                onOpenLicenses = { showLicences = true }
                            )
                        }
                    }
                }

                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        boostProgress?.let { progress ->
            BoostPopup(
                progress = progress,
                onDismiss = { boostProgress = null },
                onLaunchGame = {
                    progress.launchPackage?.let { pkg ->
                        actions.launchIntentFor(pkg)?.let { actions.start(context, it) }
                    }
                    boostProgress = null
                }
            )
        }

        when (prompt) {
            Prompt.DND -> PermissionDialog(
                title = "Permission required",
                message = "Gaming Focus uses Android's notification policy access. Grant it in " +
                    "system settings and FF BoostX can set a priority-only filter, then restore " +
                    "your previous one. Calls are never rejected.",
                confirmLabel = "Open settings",
                onConfirm = {
                    actions.start(context, actions.dndAccessIntent())
                    prompt = Prompt.NONE
                },
                onDismiss = { prompt = Prompt.NONE }
            )

            Prompt.WRITE_SETTINGS -> PermissionDialog(
                title = "Permission required",
                message = "Brightness and screen timeout need the Modify system settings " +
                    "permission. Previous values are saved first, so every change can be undone.",
                confirmLabel = "Open settings",
                onConfirm = {
                    actions.start(context, actions.writeSettingsIntent())
                    prompt = Prompt.NONE
                },
                onDismiss = { prompt = Prompt.NONE }
            )

            Prompt.OVERLAY -> PermissionDialog(
                title = "Permission required",
                message = "The HUD and crosshair need the Display over other apps permission. " +
                    "The overlay is not touchable and not focusable, so it can draw over the " +
                    "screen but can never read it or intercept your taps.",
                confirmLabel = "Open settings",
                onConfirm = {
                    actions.start(context, actions.overlayPermissionIntent())
                    prompt = Prompt.NONE
                },
                onDismiss = { prompt = Prompt.NONE }
            )

            Prompt.USAGE -> PermissionDialog(
                title = "Usage access",
                message = "To take the overlay down on its own when the game closes, FF BoostX " +
                    "needs usage access. It reads only which app is in the foreground. An " +
                    "accessibility service would also work but could read screen content, which " +
                    "is far more access than this needs.",
                confirmLabel = "Open settings",
                onConfirm = {
                    actions.start(context, actions.usageAccessIntent())
                    prompt = Prompt.NONE
                },
                onDismiss = { prompt = Prompt.NONE }
            )

            Prompt.NONE -> Unit
        }

        if (showLicences) {
            LicencesDialog(onDismiss = { showLicences = false })
        }
    }
}

/**
 * Ten destinations is more than a bottom bar can carry legibly, so navigation is
 * a scrollable strip. The selected pill stays visible because the strip starts
 * at the left and the labels are short.
 */
@Composable
private fun TabStrip(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Tab.entries.forEach { entry ->
            val isSelected = entry == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) {
                            BoostColors.Ember.copy(alpha = 0.16f)
                        } else {
                            BoostColors.Surface.copy(alpha = 0.7f)
                        }
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isSelected) {
                                BoostColors.Ember.copy(alpha = 0.55f)
                            } else {
                                BoostColors.LineSoft
                            }
                        ),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(entry) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics {
                        contentDescription =
                            if (isSelected) "${entry.label}, selected" else entry.label
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) BoostColors.Ember else BoostColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    PremiumDialog(onDismissRequest = onDismiss) {
        Text(
            text = "Open-source licences",
            style = MaterialTheme.typography.headlineSmall,
            color = BoostColors.TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "FF BoostX is built on Jetpack Compose, AndroidX Activity and AndroidX " +
                "Lifecycle, all under the Apache License 2.0, plus the Kotlin standard library. " +
                "No analytics, advertising or tracking libraries are included, and the app makes " +
                "no network requests other than the latency and DNS probes you start yourself.",
            style = MaterialTheme.typography.bodySmall,
            color = BoostColors.TextSecondary
        )
        Spacer(Modifier.height(16.dp))
        DialogButton(
            label = "Close",
            filled = true,
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
