import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services) // reads app/google-services.json (Firebase project arrows-puzzle-84166; kept out of git)
}

// Release signing: keystore.properties (see keystore.properties.example) in ~/.arrow-escape-signing/ (preferred: keeps
// the key out of this folder) or the project root. Without it the release build is left unsigned.
val keystoreProps = Properties().apply {
    listOf(File(System.getProperty("user.home"), ".arrow-escape-signing/keystore.properties"), rootProject.file("keystore.properties"))
        .firstOrNull { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.arrowescape.game"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.vishwajit.arrowescape" // Play package (permanent); the code package (namespace) stays com.arrowescape.game
        minSdk = 24
        targetSdk = 37
        versionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").get()
        fun ad(key: String) = providers.gradleProperty("ARROW_ADMOB_$key").get()
        manifestPlaceholders["admobAppId"] = ad("APP_ID")
        listOf("BANNER", "INTERSTITIAL", "REWARDED", "APP_OPEN").forEach { buildConfigField("String", "ADMOB_${it}_ID", "\"${ad(it + "_ID")}\"") }
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) create("release") {
            storeFile = File(keystoreProps.getProperty("storeFile")).let { if (it.isAbsolute) it else rootProject.file(it.path) }
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug { // Google's public test ids: real ads must never be clicked during development
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            mapOf(
                "BANNER" to "9214589741", "INTERSTITIAL" to "1033173712", "REWARDED" to "5224354917", "APP_OPEN" to "9257395921",
            ).forEach { (k, v) -> buildConfigField("String", "ADMOB_${k}_ID", "\"ca-app-pub-3940256099942544/$v\"") }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
}

kotlin { jvmToolchain(17) }

// Shipping Google's test ad ids would earn nothing, so say so loudly when a release is built with them.
tasks.configureEach {
    if (name == "bundleRelease" || name == "assembleRelease") doFirst {
        if (providers.gradleProperty("ARROW_ADMOB_APP_ID").get().contains("3940256099942544"))
            logger.warn("WARNING: building a release with Google's TEST AdMob ids; set the ARROW_ADMOB_* ids in ~/.gradle/gradle.properties")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore)
    implementation(libs.coroutines.android)
    implementation(libs.core.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.play.review)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.org.json) // real org.json for JVM tests (android.jar only has stubs)
}
