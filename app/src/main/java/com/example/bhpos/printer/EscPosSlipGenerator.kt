package com.example.bhpos.printer

import com.example.bhpos.domain.model.StockTransaction
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EscPosSlipGenerator {

    private val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

    fun generatePreviewText(tx: StockTransaction, paperWidthMm: Int = 80): String {
        val width = if (paperWidthMm == 58) 32 else 42
        val dividerEqual = "=".repeat(width)
        val dividerDash = "-".repeat(width)

        val dateStr = dateFormat.format(Date(tx.timestamp))
        val timeStr = timeFormat.format(Date(tx.timestamp))

        val sb = StringBuilder()
        sb.append(dividerEqual).append("\n")
        sb.append(centerText("CEMENTTRACK GODOWN", width)).append("\n")
        sb.append(centerText("CENTRAL DEPOT - GODOWN #4", width)).append("\n")
        sb.append(centerText("Tel: 020-27491100 / 98220-44102", width)).append("\n")
        sb.append(centerText("GSTIN: 27AAAAA0000A1Z5", width)).append("\n")
        sb.append(dividerEqual).append("\n")
        sb.append(centerText("** GATE PASS / DISPATCH SLIP **", width)).append("\n")
        sb.append(dividerDash).append("\n")

        sb.append(twoCol("Slip No: ${tx.slipNo}", "BAY 2 OUTWARD", width)).append("\n")
        sb.append(twoCol("Date: $dateStr", "Time: $timeStr", width)).append("\n")
        sb.append(dividerDash).append("\n")

        sb.append("PARTY DETAILS:\n")
        sb.append("  Customer: ${tx.partyName}\n")
        if (tx.destinationSite.isNotBlank()) {
            sb.append("  Site: ${tx.destinationSite}\n")
        }
        sb.append("  Vehicle : ${tx.vehicleNo}\n")
        sb.append("  Driver  : ${tx.driverName} (${tx.driverPhone})\n")
        sb.append("  Challan : ${tx.challanNo}\n")
        if (tx.ewbNo.isNotBlank()) {
            sb.append("  E-Way B : ${tx.ewbNo}\n")
        }
        sb.append(dividerDash).append("\n")

        if (paperWidthMm == 58) {
            sb.append(threeCol("ITEM", "BAG", "MT", width)).append("\n")
        } else {
            sb.append(threeCol("ITEM / BATCH", "BAGS", "WT(MT)", width)).append("\n")
        }
        sb.append(dividerDash).append("\n")

        for (item in tx.items) {
            val nameLine = item.productName
            sb.append(threeCol(nameLine, "${item.quantityBags}", String.format(Locale.ENGLISH, "%.2f", item.metricTons), width)).append("\n")
            if (paperWidthMm != 58) {
                sb.append("  [${item.bayLocation} • Batch: ${item.batchNo}]\n")
            }
        }

        sb.append(dividerDash).append("\n")
        sb.append(twoCol("TOTAL BAGS:", "${tx.totalBags} BAGS", width)).append("\n")
        sb.append(twoCol("TOTAL NET WT:", String.format(Locale.ENGLISH, "%.2f MT", tx.totalMetricTons), width)).append("\n")
        sb.append(twoCol("TOTAL VALUE:", String.format(Locale.ENGLISH, "INR %.2f", tx.totalAmount), width)).append("\n")
        sb.append(dividerEqual).append("\n")

        sb.append(centerText("[QR CODE VERIFICATION]", width)).append("\n")
        sb.append(centerText("SLIP-ID: ${tx.slipNo} | TRUCK: ${tx.vehicleNo}", width)).append("\n")
        sb.append(dividerDash).append("\n")
        sb.append("\n\n")
        sb.append(twoCol("Driver Signature", "Gate Officer", width)).append("\n")
        sb.append(dividerEqual).append("\n")

        return sb.toString()
    }

    fun generateEscPosBytes(tx: StockTransaction, paperWidthMm: Int = 80): ByteArray {
        val out = ByteArrayOutputStream()

        // ESC @: Initialize printer
        out.write(byteArrayOf(0x1B, 0x40))

        // ESC a 1: Center alignment
        out.write(byteArrayOf(0x1B, 0x61, 0x01))

        // GS ! 17: Double height and double width for title
        out.write(byteArrayOf(0x1D, 0x21, 0x11))
        out.write("CEMENTTRACK\n".toByteArray(Charsets.US_ASCII))

        // Reset text size
        out.write(byteArrayOf(0x1D, 0x21, 0x00))
        out.write("GODOWN #4 - CENTRAL DEPOT\n".toByteArray(Charsets.US_ASCII))

        // ESC a 0: Left alignment
        out.write(byteArrayOf(0x1B, 0x61, 0x00))

        val bodyText = generatePreviewText(tx, paperWidthMm)
        out.write(bodyText.toByteArray(Charsets.US_ASCII))

        // Feed paper
        out.write(byteArrayOf(0x1B, 0x64, 0x04))

        // GS V 66 0: Full cut
        out.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))

        return out.toByteArray()
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
        } else {
            "$left $right"
        }
    }

    private fun threeCol(c1: String, c2: String, c3: String, width: Int): String {
        val col3Width = 8
        val col2Width = 8
        val col1Width = maxOf(1, width - col2Width - col3Width)

        val trimmedC1 = if (c1.length > col1Width) c1.take(col1Width - 1) + " " else c1.padEnd(col1Width)
        val trimmedC2 = c2.padStart(col2Width)
        val trimmedC3 = c3.padStart(col3Width)
        return trimmedC1 + trimmedC2 + trimmedC3
    }
}
