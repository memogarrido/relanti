# Relanti

Native iOS and Android app for the Spanish DGT theory exam (permiso B). A road trip to the exam with a GPS guide and your own car.

- `shared/` Kotlin Multiplatform core: study rules, sessions, rewards, content pack loader. No UI.
- `androidApp/` Jetpack Compose app.
- `iosApp/` SwiftUI app (Xcode project generated with XcodeGen from `project.yml`).
- `content/packs/relanti-b.json` the content pack both apps bundle. It is produced by the content pipeline (`cd content && python -m pipeline build` on the content branch). `content/sample/make_sample_pack.py` can still write a 40-question placeholder pack for testing.
- `content/media/app/` renders used by both apps (GPS, cars, windscreen views).
- `docs/` design and architecture (on their own branches for now).

## Run it

### Core tests (any machine with JDK 17+)

    ./gradlew :shared:jvmTest -Prelanti.android=false

### Android

Open the repo folder in Android Studio, let Gradle sync, pick the `androidApp` run configuration and press Run. Or from a terminal with the Android SDK installed:

    ./gradlew :androidApp:installDebug

Every CI run also uploads a debug APK (`relanti-debug-apk` artifact) that you can sideload.

### iOS (Mac with Xcode 16+ and a JDK)

    brew install xcodegen
    cd iosApp && xcodegen && open Relanti.xcodeproj

Pick an iPhone simulator and press Run. The first build is slow because a build phase compiles the Kotlin core with Gradle. The phase looks for Android Studio's bundled JDK or `/usr/libexec/java_home`; set `JAVA_HOME` if neither exists. To run on your own iPhone, choose your team under Signing & Capabilities.

## Testing tips

- Yo › Pase de autopista (prueba) opens every etapa and unlimited simulacros until real purchases exist.
- Tickets need good rounds on two different days. To test without waiting, change the phone's date.
- Yo › Borrar todo starts again from the onboarding.
