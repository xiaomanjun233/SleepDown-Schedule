plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.itos.coui"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
    }
}

dependencies {
    api(platform("androidx.compose:compose-bom-alpha:2026.08.01"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.animation:animation")
    api("androidx.compose.foundation:foundation:1.12.0")
    implementation("androidx.compose.runtime:runtime:1.12.0")
    implementation("androidx.dynamicanimation:dynamicanimation") {
        version { strictly("1.0.0") }
    }
    implementation("androidx.core:core-ktx:1.19.1")
}
