#!/data/data/com.termux/files/usr/bin/env bash
set -e

echo "=== Android Debug Bridge (ADB) Diagnostics & Test Suite ==="

if ! command -v adb >/dev/null 2>&1; then
    echo "[ERROR] adb is not installed in PATH."
    exit 1
fi

echo "[INFO] ADB Binary: $(command -v adb)"
adb version

echo ""
echo "=== Checking Attached Android Devices ==="
DEVICES=$(adb devices | grep -v "List of devices attached" | grep -v "^$" || true)

if [ -z "$DEVICES" ]; then
    echo "[WARN] No active ADB device or emulator connected yet."
    echo ""
    echo "To connect via Wireless Debugging directly on this Android device:"
    echo "  1. Open Android Settings -> Developer Options -> Wireless debugging."
    echo "  2. Note the IP address and Port (e.g. 192.168.x.x:PORT or localhost:PORT)."
    echo "  3. Run: adb pair <ip>:<port> (if first time pairing)"
    echo "  4. Run: adb connect <ip>:<port>"
    echo "  5. Re-run this test script: ./test_adb.sh"
    exit 0
fi

echo "[SUCCESS] Connected devices:"
adb devices -l

echo ""
echo "=== Running Device Diagnostic Tests ==="
for DEVICE_ID in $(adb devices | grep -v "List of devices attached" | grep -v "^$" | awk '{print $1}'); do
    echo "--- Device: $DEVICE_ID ---"
    echo "  Model:   $(adb -s "$DEVICE_ID" shell getprop ro.product.model)"
    echo "  Android: $(adb -s "$DEVICE_ID" shell getprop ro.build.version.release) (SDK $(adb -s "$DEVICE_ID" shell getprop ro.build.version.sdk))"
    echo "  Arch:    $(adb -s "$DEVICE_ID" shell getprop ro.product.cpu.abi)"
    echo "  Battery: $(adb -s "$DEVICE_ID" shell dumpsys battery | grep level | tr -d ' ')"
done

echo ""
echo "=== All ADB Diagnostic Checks Completed ==="
