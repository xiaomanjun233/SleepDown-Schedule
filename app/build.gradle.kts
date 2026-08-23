import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.regex.Pattern

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.baselineprofile")
}

fun releaseSecret(propertyName: String, environmentName: String): String? =
    providers.gradleProperty(propertyName)
        .orElse(providers.environmentVariable(environmentName))
        .orNull

val releaseStoreFilePath = releaseSecret("sleepdown.releaseStoreFile", "SLEEPDOWN_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSecret("sleepdown.releaseStorePassword", "SLEEPDOWN_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSecret("sleepdown.releaseKeyAlias", "SLEEPDOWN_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSecret("sleepdown.releaseKeyPassword", "SLEEPDOWN_RELEASE_KEY_PASSWORD")
val remoteConfigSecret = releaseSecret("sleepdown.remoteConfigSecret", "SLEEPDOWN_REMOTE_CONFIG_SECRET").orEmpty()
val skipReleaseResourceShrink = providers.gradleProperty("sleepdown.skipReleaseResourceShrink")
    .map(String::toBoolean)
    .getOrElse(false)
val enableLargeGlassExperiment = providers.gradleProperty("sleepdown.enableLargeGlassExperiment")
    .map(String::toBoolean)
    .getOrElse(false)
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

@Suppress("UnstableApiUsage")
android {
    namespace = "com.xiaomanjun.sleepdownschedule"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        disable += setOf(
            "NullSafeMutableLiveData",
            "RememberInComposition",
            "FrequentlyChangingValue",
            "AutoboxingStateCreation",
            "ObsoleteLintCustomCheck",
            "GradleDependency",
            "VectorPath",
            "NestedWeights",
            "UnusedResources",
            "IconLauncherShape",
            "IconLocation",
            "IconDuplicates",
            // API 37 is still used only for compilation; changing target behavior is a release decision.
            "OldTargetApi",
            // The benchmark variant must stay unshrunk so baseline-profile tooling can inspect it.
            "NotShrinkingResources"
        )
    }

    defaultConfig {
        applicationId = "com.xiaomanjun.sleepdownschedule"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SLEEPDOWN_API_BASE_URL", "\"https://api.sleepdownschedule.cn\"")
        buildConfigField(
            "boolean",
            "SLEEPDOWN_LARGE_GLASS_EXPERIMENT",
            enableLargeGlassExperiment.toString()
        )
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
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
        getByName("debug") {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "SLEEPDOWN_REMOTE_CONFIG_SECRET", "\"\"")
            buildConfigField("boolean", "SLEEPDOWN_REMOTE_AI_ENABLED", "false")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = !skipReleaseResourceShrink
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "SLEEPDOWN_REMOTE_CONFIG_SECRET", "\"$remoteConfigSecret\"")
            buildConfigField("boolean", "SLEEPDOWN_REMOTE_AI_ENABLED", remoteConfigSecret.isNotBlank().toString())
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".benchmark"
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"github\"")
        }
        create("store") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"store\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

androidComponents {
    onVariants(selector().withName(Pattern.compile("(github|store)BenchmarkRelease"))) { variant ->
        variant.applicationId.set("${variant.applicationId.get()}.benchmark")
    }
}

tasks.configureEach {
    val createsReleaseArtifact = name.matches(Regex("(assemble|bundle|package).*(Release)$"))
    if (createsReleaseArtifact) {
        doFirst {
            check(hasReleaseSigning) {
                "SleepDown release signing is missing. Configure sleepdown.releaseStoreFile, " +
                    "sleepdown.releaseStorePassword, sleepdown.releaseKeyAlias and " +
                    "sleepdown.releaseKeyPassword (or the matching SLEEPDOWN_RELEASE_* environment variables)."
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

ksp {
    arg("room.schemaLocation", file("$projectDir/schemas").path)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.runtime:runtime-tracing")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.metrics:metrics-performance:1.0.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.palette:palette-ktx:1.0.0")
    compileOnly("com.oplus.animation:viewseamless:1.0.0@aar")
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.3")
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.8.3")
    baselineProfile(project(":benchmark"))
}
