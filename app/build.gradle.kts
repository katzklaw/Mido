import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Google Maps/Places key lives in local.properties (never committed): MAPS_API_KEY=...
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.mido"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.mido"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "0.11.7"
        // Developer switch: PRO_UNLOCKED=true in local.properties unlocks every Pro feature in your own build.
        buildConfigField("boolean", "PRO_UNLOCKED", localProps.getProperty("PRO_UNLOCKED", "false").trim().lowercase().let { if (it == "true") "true" else "false" })
        // Your licence public key (printed by `java tools/KeyTool.java init`). Kept here so a new project zip never overwrites it.
        buildConfigField("String", "PRO_PUBLIC_KEY", "\"${localProps.getProperty("PRO_PUBLIC_KEY", "").filter { !it.isWhitespace() }}\"")
        buildConfigField("String", "MAPS_API_KEY", "\"${localProps.getProperty("MAPS_API_KEY", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
