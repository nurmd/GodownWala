#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "=== Building & Packaging GodownWala POS APK ==="
cd /data/data/com.termux/files/home/bhpos

ANDROID_JAR="/data/data/com.termux/files/home/android-sdk/platforms/android-26/android.jar"

# 1. Compile Resources with aapt2
mkdir -p build/compiled-res build/gen build/app-classes build/dex
echo "[1/6] Compiling resources with aapt2..."
aapt2 compile --dir app/src/main/res -o build/compiled-res/

# 2. Link APK with assets
echo "[2/6] Linking base APK with assets..."
aapt2 link -I /system/framework/framework-res.apk \
  -A app/src/main/assets \
  --manifest app/src/main/AndroidManifest.xml \
  --min-sdk-version 24 \
  --target-sdk-version 35 \
  --version-code 1 \
  --version-name "1.0" \
  -o build/base.apk \
  build/compiled-res/*.flat \
  --java build/gen \
  --auto-add-overlay

# 3. Compile Kotlin Application Code
echo "[3/6] Compiling Kotlin application sources..."
kotlinc -cp "$ANDROID_JAR:test-libs/coroutines.jar" \
  app/src/main/java/com/example/bhpos/domain/model/Models.kt \
  app/src/main/java/com/example/bhpos/data/repository/CementStockRepository.kt \
  app/src/main/java/com/example/bhpos/data/repository/CementStockRepositoryImpl.kt \
  app/src/main/java/com/example/bhpos/printer/BluetoothPrinterManager.kt \
  app/src/main/java/com/example/bhpos/printer/EscPosSlipGenerator.kt \
  app/src/main/java/com/example/bhpos/MainActivity.kt \
  -d build/app-classes

# 4. Compile Dex with d8
echo "[4/6] Compiling DEX bytecode with d8..."
d8 --lib "$ANDROID_JAR" \
  --min-api 24 \
  $(find build/app-classes -name "*.class") \
  test-libs/coroutines.jar \
  /data/data/com.termux/files/usr/opt/kotlin/lib/kotlin-stdlib.jar \
  --output build/dex/

# 5. Package APK
echo "[5/6] Packaging classes.dex and assets into APK..."
cp build/base.apk build/godownwala-unaligned.apk
(cd build/dex && zip -u ../godownwala-unaligned.apk classes.dex)

# 6. Sign APK
echo "[6/6] Signing APK with debug certificate..."
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -v -keystore debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
fi
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out build/GodownWala.apk build/godownwala-unaligned.apk

# Copy to Downloads
mkdir -p /data/data/com.termux/files/home/storage/downloads
cp build/GodownWala.apk /data/data/com.termux/files/home/storage/downloads/GodownWala.apk

echo "=== APK Successfully Built & Signed! ==="
echo "Location: /data/data/com.termux/files/home/storage/downloads/GodownWala.apk"
