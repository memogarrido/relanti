// All plugins sit on one root classpath so the Kotlin plugin can see the
// Android plugin. The Android plugin is only added when the Android app is part
// of the build (see settings.gradle.kts).
buildscript {
    val withAndroid = providers.gradleProperty("relanti.android").orNull != "false"
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
    dependencies {
        val kotlinVersion = "2.3.21"
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        if (withAndroid) {
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
            classpath("com.android.tools.build:gradle:8.13.0")
        }
    }
}
