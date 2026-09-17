#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "=== Building & Packaging CementTrack APK ==="
cd /data/data/com.termux/files/home/bhpos

# 1. Compile Resources with aapt2
mkdir -p build/compiled-res build/gen build/dex
echo "[1/5] Compiling resources with aapt2..."
aapt2 compile --dir app/src/main/res -o build/compiled-res/

# 2. Link APK
echo "[2/5] Linking base APK..."
aapt2 link -I /system/framework/framework-res.apk \
  --manifest app/src/main/AndroidManifest.xml \
  -o build/base.apk \
  build/compiled-res/*.flat \
  --java build/gen \
  --auto-add-overlay

# 3. Compile Dex with d8
echo "[3/5] Compiling DEX bytecode with d8..."
d8 --lib /system/framework/framework.jar \
  $(find build/test-classes -name "*.class" ! -name "*Test*") \
  test-libs/coroutines.jar \
  /data/data/com.termux/files/usr/opt/kotlin/lib/kotlin-stdlib.jar \
  --output build/dex/

# 4. Package APK
echo "[4/5] Packaging classes.dex into APK..."
cp build/base.apk build/bhpos-unaligned.apk
(cd build/dex && zip -u ../bhpos-unaligned.apk classes.dex)

# 5. Sign APK
echo "[5/5] Signing APK with debug certificate..."
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -v -keystore debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
fi
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out build/bhpos-debug.apk build/bhpos-unaligned.apk

# Copy to Downloads
cp build/bhpos-debug.apk /data/data/com.termux/files/home/storage/downloads/bhpos-debug.apk

echo "=== APK Successfully Built & Signed! ==="
echo "Location: /data/data/com.termux/files/home/storage/downloads/bhpos-debug.apk"
