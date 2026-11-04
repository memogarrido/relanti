rootProject.name = "relanti"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

// The Android app needs the Android SDK. Builds that only need the shared core
// (the iOS framework, the core tests, content tools) pass -Prelanti.android=false.
val withAndroid = providers.gradleProperty("relanti.android").orNull != "false"

include(":shared")
if (withAndroid) include(":androidApp")
