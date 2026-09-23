plugins {
    id("com.android.application")
}

fun releaseSecret(propertyName: String, environmentName: String): String? =
    providers.gradleProperty(propertyName)
        .orElse(providers.environmentVariable(environmentName))
        .orNull

val releaseStoreFilePath = releaseSecret("sleepdown.releaseStoreFile", "SLEEPDOWN_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSecret("sleepdown.releaseStorePassword", "SLEEPDOWN_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSecret("sleepdown.releaseKeyAlias", "SLEEPDOWN_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSecret("sleepdown.releaseKeyPassword", "SLEEPDOWN_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.suda.yzune.wakeupschedule"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.suda.yzune.wakeupschedule"
        minSdk = 33
        targetSdk = 36
        versionCode = 257
        versionName = "6.0.17"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFilePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.configureEach {
    if (name.matches(Regex("(assemble|bundle|package).*Release$"))) {
        doFirst {
            check(hasReleaseSigning) {
                "SleepDown release signing is missing. Configure the shared sleepdown.release* properties " +
                    "or matching SLEEPDOWN_RELEASE_* environment variables."
            }
        }
    }
}
