package com.example.bhpos.printer

import com.example.bhpos.domain.model.StockTransaction
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PrintOptions(
    val showHeader: Boolean = true,
    val showSlipMeta: Boolean = true,
    val showCustomer: Boolean = true,
    val showSite: Boolean = true,
    val showTransport: Boolean = true,
    val showChallanEwb: Boolean = true,
    val showBatchBay: Boolean = true,
    val showAmount: Boolean = true,
    val showQrVerification: Boolean = true,
    val showSignatures: Boolean = true
) {
    fun toJson(): String {
        return """{"showHeader":$showHeader,"showSlipMeta":$showSlipMeta,"showCustomer":$showCustomer,"showSite":$showSite,"showTransport":$showTransport,"showChallanEwb":$showChallanEwb,"showBatchBay":$showBatchBay,"showAmount":$showAmount,"showQrVerification":$showQrVerification,"showSignatures":$showSignatures}"""
    }

    companion object {
        fun fromJson(jsonStr: String?): PrintOptions {
            if (jsonStr.isNullOrBlank() || jsonStr == "{}") return PrintOptions()
            return try {
                fun parseBool(key: String, default: Boolean): Boolean {
                    val pattern = Regex("\"$key\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
                    val match = pattern.find(jsonStr) ?: return default
                    return match.groupValues[1].equals("true", ignoreCase = true)
                }
                PrintOptions(
                    showHeader = parseBool("showHeader", true),
                    showSlipMeta = parseBool("showSlipMeta", true),
                    showCustomer = parseBool("showCustomer", true),
                    showSite = parseBool("showSite", true),
                    showTransport = parseBool("showTransport", true),
                    showChallanEwb = parseBool("showChallanEwb", true),
                    showBatchBay = parseBool("showBatchBay", true),
                    showAmount = parseBool("showAmount", true),
                    showQrVerification = parseBool("showQrVerification", true),
                    showSignatures = parseBool("showSignatures", true)
                )
            } catch (_: Exception) {
                PrintOptions()
            }
        }
    }
}

object EscPosSlipGenerator {

    private val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

    @JvmOverloads
    fun generatePreviewText(
        tx: StockTransaction,
        paperWidthMm: Int = 80,
        options: PrintOptions = PrintOptions()
    ): String {
        val width = if (paperWidthMm == 58) 32 else 48
        val dividerEqual = "=".repeat(width)
        val dividerDash = "-".repeat(width)

        val dateStr = dateFormat.format(Date(tx.timestamp))
        val timeStr = timeFormat.format(Date(tx.timestamp))

        val sb = StringBuilder()

        // 1. Header & Contact
        if (options.showHeader) {
            sb.append(dividerEqual).append("\n")
            sb.append(centerText("CEMENTTRACK GODOWN", width)).append("\n")
            sb.append(centerText("CENTRAL DEPOT - GODOWN #4", width)).append("\n")
            sb.append(centerText("Tel: 020-27491100 / 98220-44102", width)).append("\n")
            sb.append(centerText("GSTIN: 27AAAAA0000A1Z5", width)).append("\n")
        }

        // Title Banner
        sb.append(dividerEqual).append("\n")
        sb.append(centerText("** GATE PASS / DISPATCH SLIP **", width)).append("\n")
        sb.append(dividerDash).append("\n")

        // 2. Slip Meta
        if (options.showSlipMeta) {
            sb.append(twoCol("Slip No: ${tx.slipNo}", "BAY 2 OUTWARD", width)).append("\n")
            sb.append(twoCol("Date: $dateStr", "Time: $timeStr", width)).append("\n")
            sb.append(dividerDash).append("\n")
        }

        // 3. Party Details
        val hasParty = options.showCustomer || options.showSite || options.showTransport || options.showChallanEwb
        if (hasParty) {
            sb.append("PARTY DETAILS:\n")
            if (paperWidthMm == 58) {
                if (options.showCustomer) appendWrappedField(sb, "Customer: ", tx.partyName, width)
                if (options.showSite && tx.destinationSite.isNotBlank()) appendWrappedField(sb, "Site    : ", tx.destinationSite, width)
                if (options.showTransport) {
                    appendWrappedField(sb, "Vehicle : ", tx.vehicleNo, width)
                    appendWrappedField(sb, "Driver  : ", tx.driverName, width)
                    if (tx.driverPhone.isNotBlank()) appendWrappedField(sb, "Phone   : ", tx.driverPhone, width)
                }
                if (options.showChallanEwb) {
                    appendWrappedField(sb, "Challan : ", tx.challanNo, width)
                    if (tx.ewbNo.isNotBlank()) appendWrappedField(sb, "E-Way B : ", tx.ewbNo, width)
                }
            } else {
                if (options.showCustomer) appendWrappedField(sb, "  Customer: ", tx.partyName, width)
                if (options.showSite && tx.destinationSite.isNotBlank()) appendWrappedField(sb, "  Site: ", tx.destinationSite, width)
                if (options.showTransport) {
                    appendWrappedField(sb, "  Vehicle : ", tx.vehicleNo, width)
                    val driverInfo = if (tx.driverPhone.isNotBlank()) "${tx.driverName} (${tx.driverPhone})" else tx.driverName
                    appendWrappedField(sb, "  Driver  : ", driverInfo, width)
                }
                if (options.showChallanEwb) {
                    appendWrappedField(sb, "  Challan : ", tx.challanNo, width)
                    if (tx.ewbNo.isNotBlank()) appendWrappedField(sb, "  E-Way B : ", tx.ewbNo, width)
                }
            }
            sb.append(dividerDash).append("\n")
        }

        // 4. Items Table
        if (paperWidthMm == 58) {
            sb.append(threeCol("ITEM", "BAG", "MT", width)).append("\n")
        } else {
            sb.append(threeCol("ITEM / BATCH", "BAGS", "WT(MT)", width)).append("\n")
        }
        sb.append(dividerDash).append("\n")

        for (item in tx.items) {
            val nameLine = item.productName
            sb.append(threeCol(nameLine, "${item.quantityBags}", String.format(Locale.ENGLISH, "%.2f", item.metricTons), width)).append("\n")
            if (options.showBatchBay && paperWidthMm != 58) {
                sb.append("  [${item.bayLocation} | Batch: ${item.batchNo}]\n")
            } else if (options.showBatchBay && paperWidthMm == 58) {
                sb.append("  [${item.batchNo}]\n")
            }
        }

        sb.append(dividerDash).append("\n")

        // 5. Totals
        sb.append(twoCol("TOTAL BAGS:", "${tx.totalBags} BAGS", width)).append("\n")
        sb.append(twoCol("TOTAL NET WT:", String.format(Locale.ENGLISH, "%.2f MT", tx.totalMetricTons), width)).append("\n")
        if (options.showAmount) {
            sb.append(twoCol("TOTAL VALUE:", String.format(Locale.ENGLISH, "INR %.2f", tx.totalAmount), width)).append("\n")
        }
        sb.append(dividerEqual).append("\n")

        // 6. QR Code Verification
        if (options.showQrVerification) {
            sb.append(centerText("[QR CODE VERIFICATION]", width)).append("\n")
            if (paperWidthMm == 58) {
                sb.append(centerText("SLIP: ${tx.slipNo}", width)).append("\n")
                sb.append(centerText("TRUCK: ${tx.vehicleNo}", width)).append("\n")
            } else {
                sb.append(centerText("SLIP-ID: ${tx.slipNo} | TRUCK: ${tx.vehicleNo}", width)).append("\n")
            }
            sb.append(dividerDash).append("\n")
        }

        // 7. Signatures
        if (options.showSignatures) {
            sb.append("\n\n")
            sb.append(twoCol("Driver Signature", "Gate Officer", width)).append("\n")
            sb.append(dividerEqual).append("\n")
        }

        return sb.toString()
    }

    @JvmOverloads
    fun generateEscPosBytes(
        tx: StockTransaction,
        paperWidthMm: Int = 80,
        options: PrintOptions = PrintOptions()
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // ESC @: Initialize printer
        out.write(byteArrayOf(0x1B, 0x40))

        // ESC t 0: Standard character code table (PC437)
        out.write(byteArrayOf(0x1B, 0x74, 0x00))

        // Print complete pre-formatted body text with active options
        val bodyText = generatePreviewText(tx, paperWidthMm, options)
        out.write(bodyText.toByteArray(Charsets.US_ASCII))

        // Feed paper past tear bar (ESC d 5 feeds 5 lines)
        out.write(byteArrayOf(0x1B, 0x64, 0x05))

        // GS V 1: Partial cut
        out.write(byteArrayOf(0x1D, 0x56, 0x01))

        return out.toByteArray()
    }

    @JvmOverloads
    fun generateTestSlipText(printerName: String, macAddress: String, paperWidthMm: Int = 80): String {

        val width = if (paperWidthMm == 58) 32 else 48
        val dividerEqual = "=".repeat(width)
        val dividerDash = "-".repeat(width)

        val dateStr = dateFormat.format(Date())
        val timeStr = timeFormat.format(Date())

        val sb = StringBuilder()
        sb.append(dividerEqual).append("\n")
        sb.append(centerText("CEMENTTRACK GODOWN", width)).append("\n")
        sb.append(centerText("BLUETOOTH PRINTER TEST", width)).append("\n")
        sb.append(centerText("CENTRAL DEPOT - GODOWN #4", width)).append("\n")
        sb.append(dividerDash).append("\n")
        sb.append("Printer: $printerName\n")
        sb.append("MAC    : $macAddress\n")
        sb.append("Date   : $dateStr  $timeStr\n")
        val formatStr = if (paperWidthMm == 58) "Format : ESC/POS Direct Link\n" else "Format : ESC/POS Standard Direct Link\n"
        sb.append(formatStr)
        sb.append("Paper  : ${paperWidthMm}mm Thermal Roll\n")
        sb.append(dividerDash).append("\n")
        sb.append(centerText("SELF TEST: OK", width)).append("\n")
        sb.append(centerText("COMMUNICATION: VERIFIED", width)).append("\n")
        sb.append(dividerEqual).append("\n")
        sb.append("\n\n")
        sb.append(twoCol("Operator Check", "Hardware Test", width)).append("\n")
        sb.append(dividerEqual).append("\n")
        return sb.toString()
    }

    @JvmOverloads
    fun generateTestSlipBytes(printerName: String, macAddress: String, paperWidthMm: Int = 80): ByteArray {
        val out = ByteArrayOutputStream()
        // ESC @: Init
        out.write(byteArrayOf(0x1B, 0x40))
        // ESC t 0: Standard character code table (PC437)
        out.write(byteArrayOf(0x1B, 0x74, 0x00))

        val text = generateTestSlipText(printerName, macAddress, paperWidthMm)
        out.write(text.toByteArray(Charsets.US_ASCII))

        // Feed paper past tear bar (ESC d 5 feeds 5 lines)
        out.write(byteArrayOf(0x1B, 0x64, 0x05))

        // GS V 1: Partial cut
        out.write(byteArrayOf(0x1D, 0x56, 0x01))
        return out.toByteArray()
    }

    private fun appendWrappedField(sb: StringBuilder, label: String, value: String, width: Int) {
        if (value.isBlank()) return
        val combined = "$label$value"
        if (combined.length <= width) {
            sb.append(combined).append("\n")
            return
        }

        if (width <= 32) {
            // 58mm compact layout:
            // Print clean label on first line, indented word-wrapped value on following line(s)
            sb.append(label.trimEnd()).append("\n")
            val maxValWidth = width - 2 // 2 spaces indent
            var remaining = value.trim()
            while (remaining.isNotEmpty()) {
                if (remaining.length <= maxValWidth) {
                    sb.append("  ").append(remaining).append("\n")
                    break
                }
                val splitIdx = remaining.take(maxValWidth).lastIndexOf(' ')
                val cut = if (splitIdx > 0) splitIdx else maxValWidth
                sb.append("  ").append(remaining.substring(0, cut).trim()).append("\n")
                remaining = remaining.substring(cut).trim()
            }
        } else {
            // 80mm layout:
            // First line has label, wrapped continuation lines indented
            val indent = " ".repeat(label.length)
            var first = true
            var remaining = value.trim()
            while (remaining.isNotEmpty()) {
                val currentIndent = if (first) label else indent
                val allowed = width - currentIndent.length
                if (remaining.length <= allowed) {
                    sb.append(currentIndent).append(remaining).append("\n")
                    break
                }
                val splitIdx = remaining.take(allowed).lastIndexOf(' ')
                val cut = if (splitIdx > 0) splitIdx else allowed
                sb.append(currentIndent).append(remaining.substring(0, cut).trim()).append("\n")
                remaining = remaining.substring(cut).trim()
                first = false
            }
        }
    }

    private fun centerText(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        val pad = (width - text.length) / 2
        return " ".repeat(pad) + text
    }

    private fun twoCol(left: String, right: String, width: Int): String {
        val space = width - left.length - right.length
        return if (space > 0) {
            left + " ".repeat(space) + right
        } else if (space == 0) {
            left + right
        } else {
            left + "\n" + right.padStart(width)
        }
    }

    private fun threeCol(c1: String, c2: String, c3: String, width: Int): String {
        val col3Width = if (width >= 48) 10 else 8
        val col2Width = if (width >= 48) 10 else 8
        val col1Width = maxOf(1, width - col2Width - col3Width)

        val trimmedC1 = if (c1.length > col1Width) c1.take(col1Width - 1) + " " else c1.padEnd(col1Width)
        val trimmedC2 = c2.padStart(col2Width)
        val trimmedC3 = c3.padStart(col3Width)
        return trimmedC1 + trimmedC2 + trimmedC3
    }
}
