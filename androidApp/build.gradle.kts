import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.garrido.relanti.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.garrido.relanti"
        minSdk = 26
        targetSdk = 36
        versionCode = (providers.gradleProperty("relanti.versionCode").orNull ?: "1").toInt()
        versionName = "0.1.0"
    }

    // Play upload key. Lives outside the repo; the paths and password come from
    // ~/.gradle/gradle.properties (RELANTI_UPLOAD_*). Without them, release falls back to the
    // debug key so local release builds still work (Play rejects those uploads).
    val uploadStore = providers.gradleProperty("RELANTI_UPLOAD_STORE_FILE").orNull
    signingConfigs {
        if (uploadStore != null) {
            create("upload") {
                storeFile = file(uploadStore)
                storePassword = providers.gradleProperty("RELANTI_UPLOAD_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("RELANTI_UPLOAD_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("RELANTI_UPLOAD_STORE_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // Compose screens render on the JVM (Robolectric, native graphics) for playthroughs and screenshots.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            it.testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL; events("failed") }
        }
    }

    // The content pack and the shared renders, read from the repo so both apps use the same files.
    sourceSets["main"].assets.srcDirs("../content/packs", "../content/media/app")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))

    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
