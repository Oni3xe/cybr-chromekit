// chromekit: the shared cybrcore Compose HUD components, theme, generated tokens and bundled fonts.
// Built only inside a consuming app (see README "Compose apps"): plugin versions, repositories and
// gradle.properties come from the app's root project.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.oni3xe.chromekit"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    // The fonts live once in ../fonts (also used by Views apps). Compose apps get Rajdhani
    // Regular/Medium/SemiBold/Bold and JetBrains Mono Regular/Bold; Rajdhani Light is Views-only.
    sourceSets["main"].res.srcDir(file("../fonts/res-core"))
    sourceSets["main"].res.srcDir(file("../fonts/res-mono-bold"))
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    api(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
}
