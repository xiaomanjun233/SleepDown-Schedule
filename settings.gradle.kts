pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
    repositories {
        providers.gradleProperty("sleepdown.localMavenPath").orNull
            ?.let(::file)
            ?.takeIf { it.isDirectory }
            ?.let { localRepository -> maven { url = localRepository.toURI() } }
        mavenLocal()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}

val useLocalMiuixBuild = providers.gradleProperty("sleepdown.useLocalMiuix")
    .map(String::toBoolean)
    .getOrElse(true)
val localMiuixBuild = if (useLocalMiuixBuild) {
    providers.gradleProperty("sleepdown.miuixSourcePath").orNull
        ?.let(::file)
        ?.takeIf { it.isDirectory }
        ?: file("../miuix-reference").takeIf { it.isDirectory }
} else {
    null
}

if (useLocalMiuixBuild && localMiuixBuild == null) {
    throw GradleException(
        "SleepDown requires the patched Miuix 0.9.3 source build. " +
            "Follow the Miuix setup commands in README.md or set sleepdown.miuixSourcePath."
    )
}

if (localMiuixBuild != null) {
    includeBuild(localMiuixBuild) {
        dependencySubstitution {
            substitute(module("top.yukonga.miuix.kmp:miuix-ui-android")).using(project(":miuix-ui"))
            substitute(module("top.yukonga.miuix.kmp:miuix-preference-android")).using(project(":miuix-preference"))
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        providers.gradleProperty("sleepdown.localMavenPath").orNull
            ?.let(::file)
            ?.takeIf { it.isDirectory }
            ?.let { localRepository -> maven { url = localRepository.toURI() } }
        mavenLocal()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "CourseSchedule"
include(":app")
include(":benchmark")

