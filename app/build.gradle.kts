plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ffboostx"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ffboostx"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "4.1"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 in full mode: shrinks and obfuscates the code and strips unused
            // resources. Compose ships its own consumer rules, so the app only
            // needs to keep its own entry points.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // An unsigned APK cannot be installed, so the release build is signed
            // with the local debug key by default. That makes the CI artifact
            // something you can actually sideload and test. It is NOT a
            // distribution key: to publish, supply a real keystore through the
            // four properties below (for example in ~/.gradle/gradle.properties
            // or as -P flags) and this block will use it instead.
            signingConfig = if (project.hasProperty("ffKeystore")) {
                signingConfigs.create("upload") {
                    storeFile = file(project.property("ffKeystore") as String)
                    storePassword = project.property("ffStorePassword") as String
                    keyAlias = project.property("ffKeyAlias") as String
                    keyPassword = project.property("ffKeyPassword") as String
                }
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates every log call; BuildConfig.VERSION_NAME feeds
        // the About section.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }

    lint {
        // A lint failure should not block the GitHub Actions APK build.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")

    // Supplies collectAsStateWithLifecycle and LocalLifecycleOwner so device
    // sampling stops automatically when the app is backgrounded.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    debugImplementation(composeBom)
    debugImplementation("androidx.compose.ui:ui-tooling")
}
