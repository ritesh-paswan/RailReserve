plugins {
    alias(libs.plugins.android.application)
}

val debugBackendUrl = providers.gradleProperty("railreserveDebugBackendUrl")
    .getOrElse("http://10.0.2.2:3000")
val releaseBackendUrl = providers.gradleProperty("railreserveBackendUrl")
    .getOrElse("https://railreserve-backend.example.com")
require(releaseBackendUrl.startsWith("https://", ignoreCase = true)) {
    "railreserveBackendUrl must use HTTPS for release builds."
}
fun buildConfigString(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.railreserve"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.railreserve"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "BACKEND_BASE_URL", buildConfigString(debugBackendUrl))
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            buildConfigField("String", "BACKEND_BASE_URL", buildConfigString(releaseBackendUrl))
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
