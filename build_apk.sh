#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "=== Building GodownWala POS APK (Gradle) ==="
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-/data/data/com.termux/files/home/android-sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

chmod +x ./gradlew
./gradlew assembleDebug --stacktrace

APK_PATH=$(find app/build/outputs/apk -name "*.apk" | head -n 1)
echo "=== APK Successfully Built! ==="
echo "Location: $APK_PATH"