import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.sirak.liftlog.wear"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.sirak.liftlog"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        getByName("debug") {
            // Wear OS shares data only between apps signed with the same certificate.
            // Leave this unset to use ~/.android/debug.keystore. To match the phone
            // app, set liftlog.debug.keystore in local.properties (that file is gitignored).
            val keystorePath = localProperties.getProperty("liftlog.debug.keystore")
            if (!keystorePath.isNullOrBlank()) {
                val keystore = file(keystorePath)
                if (!keystore.isFile) {
                    throw GradleException(
                        "liftlog.debug.keystore points at ${keystore.absolutePath}, which does not exist.",
                    )
                }
                storeFile = keystore
                storePassword = localProperties.getProperty("liftlog.debug.storePassword") ?: "android"
                keyAlias = localProperties.getProperty("liftlog.debug.keyAlias") ?: "androiddebugkey"
                keyPassword = localProperties.getProperty("liftlog.debug.keyPassword") ?: "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
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
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.play.services.wearable)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
}
