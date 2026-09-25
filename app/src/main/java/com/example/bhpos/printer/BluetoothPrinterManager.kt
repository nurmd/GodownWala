package com.example.bhpos.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * ============================================================================
 * MODULE: BLUETOOTH THERMAL PRINTER MANAGER & OFFLINE FAILOVER ENGINE
 * ============================================================================
 * Manages Bluetooth SPP / RFCOMM connections to POS thermal printers with automatic
 * offline printer failover resilience.
 *
 * Futureproofing Invariants & Architectural Rules:
 * 1. DISCOVERY CANCELLATION: Android cannot reliably open RFCOMM sockets while discovery
 *    or inquiry is running. Discovery is explicitly halted before any connection attempt.
 * 2. AUTOMATIC OFFLINE PRINTER FAILOVER:
 *    - When the primary/saved printer (e.g. SR588) is turned off, out of range, or unpowered,
 *      the manager will not fail the print job.
 *    - It iterates through all other bonded thermal printer candidates (e.g. MPT-III, generic POS)
 *      and routes the print job to the first online printer available.
 *    - Reports back [PrintResult.isFailover] so the host application can update persisted settings.
 * 3. 3-TIER RFCOMM FALLBACK:
 *    - Tier 1: Standard RFCOMM via SPP UUID (00001101-0000-1000-8000-00805F9B34FB).
 *    - Tier 2: Insecure RFCOMM socket (bypasses pairing handshake issues on generic hardware).
 *    - Tier 3: Java reflection fallback to channel 1 (`createRfcommSocket(1)`).
 * 4. UNBUFFERED STREAM FLUSH: Direct write followed by [flush] and a 200ms grace period
 *    prevents socket timeouts or truncated slips.
 */
object BluetoothPrinterManager {

    private const val TAG = "BH_PRINTER"
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * Data outcome of a print attempt.
     *
     * @property success Whether bytes were successfully written and flushed to a printer.
     * @property deviceName The name of the printer that actually accepted the job.
     * @property deviceAddress The MAC address of the printer that accepted the job.
     * @property isFailover True if the primary configured printer was offline and an alternative
     *           bonded printer was used instead.
     * @property errorMessage Descriptive diagnostic message in case of failure.
     */
    data class PrintResult(
        val success: Boolean,
        val deviceName: String? = null,
        val deviceAddress: String? = null,
        val isFailover: Boolean = false,
        val paperWidthMm: Int = 80,
        val errorMessage: String? = null
    )

    /**
     * Determines the paper roll width (58mm or 80mm) from the printer model or name string.
     */
    fun detectPaperWidthFromName(name: String?): Int {
        val n = (name ?: "").lowercase(Locale.ROOT)
        return when {
            n.contains("58") || n.contains("sr588") || n.contains("sr-588") ||
            n.contains("mpt-ii") || n.contains("mpt-2") || n.contains("pt-210") ||
            n.contains("rpp02") || n.contains("zj-58") || n.contains("pos-58") ||
            n.contains("pos58") || n.contains("bt-58") || n.contains("qs-58") ||
            n.contains("ep-58") || n.contains("2 inch") || n.contains("2\"") -> 58

            n.contains("80") || n.contains("mpt-iii") || n.contains("mpt-3") ||
            n.contains("rpp04") || n.contains("zj-80") || n.contains("pos-80") ||
            n.contains("pos80") || n.contains("bt-80") || n.contains("qs-80") ||
            n.contains("ep-80") || n.contains("3 inch") || n.contains("3\"") -> 80

            else -> 80
        }
    }

    /**
     * Queries printer model string over an active RFCOMM socket using ESC/POS standard query.
     */
    fun queryPrinterModelOrName(socket: BluetoothSocket): String? {
        return try {
            val os = socket.outputStream
            val inputStream = socket.inputStream
            while (inputStream.available() > 0) {
                inputStream.read()
            }
            // Send GS I 67 (0x1D, 0x49, 0x43) - Transmit printer name
            os.write(byteArrayOf(0x1D, 0x49, 0x43))
            os.flush()

            val buffer = ByteArray(128)
            var totalRead = 0
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 350 && totalRead < buffer.size) {
                if (inputStream.available() > 0) {
                    val r = inputStream.read(buffer, totalRead, buffer.size - totalRead)
                    if (r > 0) {
                        totalRead += r
                        if (buffer[totalRead - 1] == 0.toByte() || buffer[totalRead - 1] == 10.toByte()) {
                            break
                        }
                    }
                } else {
                    Thread.sleep(25)
                }
            }
            if (totalRead > 0) {
                val str = String(buffer, 0, totalRead).trim()
                if (str.isNotBlank()) str else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Determines whether a Bluetooth device matches common thermal printer naming
     * patterns or Bluetooth class identifiers.
     */
    fun isLikelyPrinter(device: BluetoothDevice): Boolean {
        val name = (device.name ?: "").lowercase(Locale.ROOT)
        val matchesName = name.contains("printer") || name.contains("pos") ||
                name.contains("mpt") || name.contains("rp") ||
                name.contains("thermal") || name.contains("slip") ||
                name.contains("bt") || name.contains("58") || name.contains("80") ||
                name.contains("sr")

        val devClass = try { device.bluetoothClass?.deviceClass ?: 0 } catch (_: Exception) { 0 }
        val majorClass = try { device.bluetoothClass?.majorDeviceClass ?: 0 } catch (_: Exception) { 0 }

        // Major Imaging (1536) or Printer (1664)
        val matchesClass = (majorClass == 1536 || devClass == 1664)
        return matchesName || matchesClass
    }

    /**
     * Attempts to send raw ESC/POS bytes to the specified printer, or automatically
     * fails over to any other active bonded thermal printer if the target is offline.
     *
     * @param bytes Optional pre-rendered ESC/POS byte sequence.
     * @param targetAddress Optional MAC address of the target printer.
     * @param fallbackAddress Optional fallback MAC address from settings.
     * @param allowFailover When false, only attempts connection to [targetAddress] without falling over.
     * @param payloadSupplier Optional callback to dynamically generate byte sequence based on actual connected device.
     * @return [PrintResult] containing status, active device, and failover state.
     */
    fun print(
        bytes: ByteArray? = null,
        targetAddress: String? = null,
        fallbackAddress: String? = null,
        allowFailover: Boolean = true,
        payloadSupplier: ((BluetoothDevice, Boolean) -> ByteArray)? = null
    ): PrintResult {
        Log.d(TAG, "Starting Bluetooth print. Target: $targetAddress, Fallback: $fallbackAddress, AllowFailover: $allowFailover")
        
        val adapter = BluetoothAdapter.getDefaultAdapter() 
            ?: return PrintResult(false, errorMessage = "Bluetooth adapter not found on this device.")

        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter is disabled.")
            return PrintResult(false, errorMessage = "Bluetooth is turned off. Please turn on Bluetooth.")
        }

        // CRITICAL: Stop discovery before attempting RFCOMM socket connection
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        } catch (_: Exception) {}

        // Build prioritized candidate list
        val candidates = mutableListOf<BluetoothDevice>()

        if (!allowFailover && !targetAddress.isNullOrBlank()) {
            // Strict targeting: ONLY attempt the designated printer
            try {
                adapter.getRemoteDevice(targetAddress)?.let { candidates.add(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve targetAddress $targetAddress: ${e.message}")
            }
            if (candidates.isEmpty()) {
                return PrintResult(false, errorMessage = "Could not find Bluetooth device for address $targetAddress.")
            }
        } else {
            // Multi-candidate priority list with intelligent failover
            // Priority 1: Specifically targeted printer MAC address
            if (!targetAddress.isNullOrBlank()) {
                try {
                    adapter.getRemoteDevice(targetAddress)?.let { candidates.add(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not resolve targetAddress $targetAddress: ${e.message}")
                }
            }

            // Priority 2: Saved active printer from application settings
            if (!fallbackAddress.isNullOrBlank() && fallbackAddress != targetAddress) {
                try {
                    adapter.getRemoteDevice(fallbackAddress)?.let { dev ->
                        if (candidates.none { it.address.equals(dev.address, ignoreCase = true) }) {
                            candidates.add(dev)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not resolve fallbackAddress $fallbackAddress: ${e.message}")
                }
            }

            // Priority 3: All other bonded thermal printers (sorted with active/known models like MPT-III first)
            val bonded = try { adapter.bondedDevices } catch (_: SecurityException) { null } ?: emptySet()
            val otherPrinters = bonded.filter { isLikelyPrinter(it) }
                .sortedByDescending { dev ->
                    val n = (dev.name ?: "").lowercase(Locale.ROOT)
                    when {
                        n.contains("mpt") -> 4   // e.g. MPT-III active printer
                        n.contains("pos") -> 3
                        n.contains("thermal") -> 2
                        else -> 1
                    }
                }

            for (dev in otherPrinters) {
                if (candidates.none { it.address.equals(dev.address, ignoreCase = true) }) {
                    candidates.add(dev)
                }
            }

            // Priority 4: Any other bonded devices as an absolute last resort
            for (dev in bonded) {
                if (candidates.none { it.address.equals(dev.address, ignoreCase = true) }) {
                    candidates.add(dev)
                }
            }
        }

        if (candidates.isEmpty()) {
            Log.e(TAG, "No bonded Bluetooth devices available.")
            return PrintResult(false, errorMessage = "No paired Bluetooth printers found in Android Settings.")
        }

        val primaryTargetAddr = if (!targetAddress.isNullOrBlank()) targetAddress else fallbackAddress

        // Iterate through candidates in priority order and attempt connection
        for (printer in candidates) {
            val pName = printer.name ?: "Unnamed Device"
            val pAddr = printer.address ?: ""
            Log.d(TAG, "Attempting connection to candidate printer: $pName ($pAddr)...")

            var socket: BluetoothSocket? = null
            try {
                // Tier 1: Standard RFCOMM via SPP UUID
                try {
                    socket = printer.createRfcommSocketToServiceRecord(SPP_UUID)
                    socket.connect()
                } catch (e1: Exception) {
                    Log.w(TAG, "Standard RFCOMM to $pName failed (${e1.message}), trying insecure socket...")
                    try {
                        socket?.close()
                    } catch (_: Exception) {}
                    
                    // Tier 2: Insecure RFCOMM socket
                    try {
                        socket = printer.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                        socket.connect()
                    } catch (e2: Exception) {
                        Log.w(TAG, "Insecure RFCOMM to $pName failed (${e2.message}), trying reflection channel 1...")
                        try {
                            socket?.close()
                        } catch (_: Exception) {}

                        // Tier 3: Java reflection fallback to RFCOMM channel 1
                        val method = printer.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        socket = method.invoke(printer, 1) as BluetoothSocket
                        socket.connect()
                    }
                }

                val isFailover = !primaryTargetAddr.isNullOrBlank() && !pAddr.equals(primaryTargetAddr, ignoreCase = true)
                var detectedWidth = detectPaperWidthFromName(pName)

                // If generic or ambiguous device name, attempt fast ESC/POS model query
                val lowerPName = pName.lowercase(Locale.ROOT)
                if (!lowerPName.contains("58") && !lowerPName.contains("80") && !lowerPName.contains("mpt") && !lowerPName.contains("sr")) {
                    queryPrinterModelOrName(socket)?.let { modelStr ->
                        val queriedWidth = detectPaperWidthFromName(modelStr)
                        detectedWidth = queriedWidth
                    }
                }

                val payload = payloadSupplier?.invoke(printer, isFailover) ?: bytes ?: ByteArray(0)

                // If connection succeeded, stream bytes directly
                val os = socket.outputStream
                os.write(payload)
                os.flush()

                // Wait before closing socket to ensure complete transfer through hardware buffers
                Thread.sleep(4500)
                socket.close()

                Log.d(TAG, "SUCCESS: Printed to $pName ($pAddr) width: ${detectedWidth}mm. Failover: $isFailover")
                return PrintResult(
                    success = true,
                    deviceName = pName,
                    deviceAddress = pAddr,
                    isFailover = isFailover,
                    paperWidthMm = detectedWidth
                )
            } catch (e: Exception) {
                try {
                    socket?.close()
                } catch (_: Exception) {}
                Log.w(TAG, "Printer $pName ($pAddr) is offline or unreachable: ${e.message}. Checking next printer...")
            }
        }

        val targetName = candidates.firstOrNull()?.let { it.name ?: it.address } ?: primaryTargetAddr ?: "target printer"
        val errMsg = if (!allowFailover) {
            "Could not connect to $targetName. Printer is offline, unpowered, or out of range."
        } else {
            val attemptedNames = candidates.joinToString(", ") { it.name ?: it.address }
            "All candidate printers ($attemptedNames) are offline or unreachable."
        }
        Log.e(TAG, errMsg)
        return PrintResult(false, errorMessage = errMsg)
    }
}
