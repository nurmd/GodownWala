# BH POS - Android Application Workspace

A modern Android Point of Sale (POS) application built using **Kotlin**, **Jetpack Compose**, and **Gradle Kotlin DSL**.

## Project Architecture & Structure

```text
bhpos/
├── app/
│   ├── build.gradle.kts           # App-level build configurations & Compose dependencies
│   ├── proguard-rules.pro         # Proguard/R8 rules
│   └── src/
│       ├── androidTest/           # Android instrumentation tests
│       │   └── java/com/example/bhpos/ExampleInstrumentedTest.kt
│       ├── main/
│       │   ├── AndroidManifest.xml # App manifest & launcher activity
│       │   ├── java/com/example/bhpos/
│       │   │   ├── MainActivity.kt # Entry point & Jetpack Compose root UI
│       │   │   └── ui/theme/       # Material 3 Theme, Colors, and Typography
│       │   │       ├── Color.kt
│       │   │       ├── Theme.kt
│       │   │       └── Type.kt
│       │   └── res/               # Android resources
│       │       └── values/
│       │           ├── strings.xml
│       │           └── themes.xml
│       └── test/                  # Local JVM unit tests
│           └── java/com/example/bhpos/ExampleUnitTest.kt
├── gradle/
│   ├── libs.versions.toml         # Gradle Version Catalog (dependencies & plugins)
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle.kts               # Root build script
├── settings.gradle.kts            # Project repositories & module definitions
├── gradle.properties              # JVM & AndroidX settings
├── gradlew / gradlew.bat          # Gradle wrapper executables
└── .gitignore                     # Git ignore rules for Android projects
```

## Key Technologies
- **Kotlin 2.1.0** with Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`)
- **Jetpack Compose & Material 3** (declarative modern Android UI)
- **Android Gradle Plugin (AGP) 8.8.0**
- **Gradle Version Catalog** (`gradle/libs.versions.toml`) for centralized dependency management
- **Target / Compile SDK**: 35 (Android 15)
- **Min SDK**: 24 (Android 7.0)

## Build & Run

### In Android Studio
Open the `bhpos` directory in Android Studio (or IntelliJ IDEA with Android plugin). Gradle will sync dependencies automatically.

### In CLI (with OpenJDK and Android SDK configured)
```bash
./gradlew assembleDebug
```
To run unit tests:
```bash
./gradlew test
```
