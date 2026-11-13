#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "=== Running GodownWala Unit Tests (Gradle) ==="
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-/data/data/com.termux/files/home/android-sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

chmod +x ./gradlew
./gradlew test --stacktrace

echo "=== All Tests Passed! ==="