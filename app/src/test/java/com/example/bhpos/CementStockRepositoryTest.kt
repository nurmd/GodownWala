package com.example.bhpos

import com.example.bhpos.data.repository.CementStockRepositoryImpl
import com.example.bhpos.domain.model.StockTransaction
import com.example.bhpos.domain.model.StockTransactionItem
import com.example.bhpos.domain.model.TransactionType
import com.example.bhpos.printer.EscPosSlipGenerator
import com.example.bhpos.printer.PrintOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CementStockRepositoryTest {

    private lateinit var repository: CementStockRepositoryImpl

    @Before
    fun setUp() {
        repository = CementStockRepositoryImpl()
    }

    @Test
    fun testInitialProductsLoaded() = runBlocking {
        val products = repository.getProductsFlow().first()
        assertTrue("Products list should not be empty", products.isNotEmpty())
        val ultraTech = products.find { it.id == "ultratech_ppc" }
        assertNotNull("UltraTech PPC must exist in seed data", ultraTech)
        assertEquals(6200, ultraTech!!.currentStockBags)
    }

    @Test
    fun testTonnageCalculation() = runBlocking {
        val products = repository.getProductsFlow().first()
        val ultraTech = products.first { it.id == "ultratech_ppc" }
        // 6,200 bags * 50 kg / 1000 = 310.0 MT
        assertEquals(310.0, ultraTech.stockMetricTons, 0.001)
    }

    @Test
    fun testRecordDispatchSuccess() = runBlocking {
        val productsBefore = repository.getProductsFlow().first()
        val initialUltraTechStock = productsBefore.first { it.id == "ultratech_ppc" }.currentStockBags

        val dispatchBags = 200
        val tx = StockTransaction(
            id = "test_tx_1",
            slipNo = "GP-TEST-01",
            type = TransactionType.OUTWARD,
            timestamp = System.currentTimeMillis(),
            partyName = "Test Contractor Ltd.",
            vehicleNo = "MH-12-AB-1234",
            driverName = "Suresh",
            driverPhone = "+91 98000-11111",
            challanNo = "CH-001",
            ewbNo = "EWB-001",
            destinationSite = "Highway Flyover Site",
            totalBags = dispatchBags,
            totalMetricTons = (dispatchBags * 50.0) / 1000.0,
            totalAmount = dispatchBags * 380.0,
            items = listOf(
                StockTransactionItem(
                    productId = "ultratech_ppc",
                    productName = "UltraTech Super PPC",
                    quantityBags = dispatchBags,
                    metricTons = (dispatchBags * 50.0) / 1000.0,
                    ratePerBag = 380.0,
                    batchNo = "BATCH-01",
                    bayLocation = "Bay A1"
                )
            )
        )

        val result = repository.recordDispatch(tx)
        assertTrue("Dispatch should succeed", result.isSuccess)

        val productsAfter = repository.getProductsFlow().first()
        val updatedStock = productsAfter.first { it.id == "ultratech_ppc" }.currentStockBags
        assertEquals(initialUltraTechStock - dispatchBags, updatedStock)

        val recordedTx = repository.getTransactionBySlipNo("GP-TEST-01")
        assertNotNull("Recorded transaction must be retrievable", recordedTx)
        assertEquals(200, recordedTx!!.totalBags)
    }

    @Test
    fun testNegativeStockPrevention() = runBlocking {
        val products = repository.getProductsFlow().first()
        val ultraTech = products.first { it.id == "ultratech_ppc" }
        val excessiveBags = ultraTech.currentStockBags + 500

        val tx = StockTransaction(
            id = "test_tx_excessive",
            slipNo = "GP-EXCESSIVE",
            type = TransactionType.OUTWARD,
            timestamp = System.currentTimeMillis(),
            partyName = "Overdraft Contractor",
            vehicleNo = "MH-12-AB-9999",
            driverName = "Ramesh",
            driverPhone = "+91 98000-22222",
            challanNo = "CH-OVER",
            ewbNo = "EWB-OVER",
            destinationSite = "Unknown Site",
            totalBags = excessiveBags,
            totalMetricTons = (excessiveBags * 50.0) / 1000.0,
            totalAmount = excessiveBags * 380.0,
            items = listOf(
                StockTransactionItem(
                    productId = ultraTech.id,
                    productName = ultraTech.name,
                    quantityBags = excessiveBags,
                    metricTons = (excessiveBags * 50.0) / 1000.0,
                    ratePerBag = ultraTech.defaultRatePerBag,
                    batchNo = ultraTech.batchNo,
                    bayLocation = ultraTech.bayLocation
                )
            )
        )

        val result = repository.recordDispatch(tx)
        assertTrue("Dispatch exceeding available stock must fail", result.isFailure)
    }

    @Test
    fun testStockInOperation() = runBlocking {
        val productsBefore = repository.getProductsFlow().first()
        val initialStock = productsBefore.first { it.id == "ambuja_opc" }.currentStockBags

        val addBags = 500
        val result = repository.recordStockIn("ambuja_opc", addBags, "AM-NEW-LOT", "Bay B1")
        assertTrue("Stock-in should succeed", result.isSuccess)

        val productsAfter = repository.getProductsFlow().first()
        val updatedStock = productsAfter.first { it.id == "ambuja_opc" }.currentStockBags
        assertEquals(initialStock + addBags, updatedStock)
    }

    @Test
    fun testEscPosSlipGeneratorFormatting() {
        val tx = StockTransaction(
            id = "tx_sample",
            slipNo = "GP-9485",
            type = TransactionType.OUTWARD,
            timestamp = 1729000000000L,
            partyName = "Vanguard Infra Projects Pvt Ltd",
            vehicleNo = "MH-12-QZ-4891",
            driverName = "Rameshwar",
            driverPhone = "+91 98234-11029",
            challanNo = "DC-2024-8841",
            ewbNo = "8921-4402-9912",
            destinationSite = "Metro Pillar 142 Site",
            totalBags = 300,
            totalMetricTons = 15.0,
            totalAmount = 114000.0,
            items = listOf(
                StockTransactionItem(
                    productId = "ultratech_ppc",
                    productName = "UltraTech PPC",
                    quantityBags = 300,
                    metricTons = 15.0,
                    ratePerBag = 380.0,
                    batchNo = "UT-24-OCT-03",
                    bayLocation = "Bay A3"
                )
            )
        )

        val slip80 = EscPosSlipGenerator.generatePreviewText(tx, 80)
        assertTrue(slip80.contains("CEMENTTRACK GODOWN"))
        assertTrue(slip80.contains("GP-9485"))
        assertTrue(slip80.contains("Vanguard Infra Projects Pvt Ltd"))
        assertTrue(slip80.contains("MH-12-QZ-4891"))
        assertTrue(slip80.contains("300 BAGS"))
        assertTrue(slip80.contains("15.00 MT"))

        val slip58 = EscPosSlipGenerator.generatePreviewText(tx, 58)
        assertTrue(slip58.contains("GP-9485"))
        assertTrue(slip58.contains("300 BAGS"))

        val rawBytes = EscPosSlipGenerator.generateEscPosBytes(tx, 80)
        assertTrue("ESC/POS byte output must not be empty", rawBytes.isNotEmpty())
        assertEquals(0x1B.toByte(), rawBytes[0]) // ESC
        assertEquals(0x40.toByte(), rawBytes[1]) // @ (Initialize)
    }

    @Test
    fun testBluetoothTestSlipGeneration() {
        val printerName = "POS-80C Mobile Thermal"
        val mac = "66:32:B1:84:DE:09"

        val text80 = EscPosSlipGenerator.generateTestSlipText(printerName, mac, 80)
        assertTrue(text80.contains("BLUETOOTH PRINTER TEST"))
        assertTrue(text80.contains(printerName))
        assertTrue(text80.contains(mac))
        assertTrue(text80.contains("COMMUNICATION: VERIFIED"))


        val text58 = EscPosSlipGenerator.generateTestSlipText(printerName, mac, 58)
        assertTrue(text58.contains("BLUETOOTH PRINTER TEST"))
        assertTrue(text58.contains(mac))

        val testBytes = EscPosSlipGenerator.generateTestSlipBytes(printerName, mac, 80)
        assertTrue("Test slip bytes must not be empty", testBytes.isNotEmpty())
        assertEquals(0x1B.toByte(), testBytes[0]) // ESC
        assertEquals(0x40.toByte(), testBytes[1]) // @
    }

    @Test
    fun testPrintOptionsJsonParsing() {
        val defaultOptions = PrintOptions.fromJson(null)
        assertTrue(defaultOptions.showHeader)
        assertTrue(defaultOptions.showAmount)
        assertTrue(defaultOptions.showBatchBay)

        val json = """{"showHeader":false,"showAmount":false,"showBatchBay":false,"showSignatures":true}"""
        val parsed = PrintOptions.fromJson(json)
        assertFalse(parsed.showHeader)
        assertFalse(parsed.showAmount)
        assertFalse(parsed.showBatchBay)
        assertTrue(parsed.showSignatures)
        assertTrue(parsed.showCustomer) // default true if missing
    }

    @Test
    fun testEscPosSlipGeneratorWithCustomOptions() {
        val tx = StockTransaction(
            id = "tx_custom_01",
            slipNo = "GP-CUSTOM-01",
            type = TransactionType.OUTWARD,
            partyName = "Skyline Towers Ltd",
            vehicleNo = "MH-14-ZZ-9999",
            driverName = "Rajesh Verma",
            driverPhone = "9988776655",
            destinationSite = "Phase 2 Sky High",
            challanNo = "DC-5555",
            ewbNo = "EWB-112233445566",
            items = listOf(
                StockTransactionItem(
                    productId = "ultratech_ppc",
                    productName = "UltraTech PPC Cement 50kg",
                    batchNo = "B26-09-01",
                    bayLocation = "BAY-02",
                    quantityBags = 100,
                    metricTons = 5.0,
                    ratePerBag = 380.0
                )
            ),
            totalBags = 100,
            totalMetricTons = 5.0,
            totalAmount = 38000.0,
            timestamp = 1726567200000L
        )

        // All fields enabled
        val allSlip = EscPosSlipGenerator.generatePreviewText(tx, 80, PrintOptions())
        assertTrue(allSlip.contains("CEMENTTRACK GODOWN"))
        assertTrue(allSlip.contains("TOTAL VALUE:"))
        assertTrue(allSlip.contains("INR 38000.00"))
        assertTrue(allSlip.contains("QR CODE VERIFICATION"))
        assertTrue(allSlip.contains("Driver Signature"))

        // Driver Pass preset: Conceals amount and batch
        val driverPassOptions = PrintOptions(
            showHeader = true,
            showSlipMeta = true,
            showCustomer = true,
            showSite = true,
            showTransport = true,
            showChallanEwb = true,
            showBatchBay = false,
            showAmount = false,
            showQrVerification = true,
            showSignatures = true
        )
        val driverSlip = EscPosSlipGenerator.generatePreviewText(tx, 80, driverPassOptions)
        assertTrue(driverSlip.contains("CEMENTTRACK GODOWN"))
        assertTrue(driverSlip.contains("Skyline Towers Ltd"))
        assertFalse("Driver pass must not contain price", driverSlip.contains("TOTAL VALUE:"))
        assertFalse("Driver pass must not contain price amount", driverSlip.contains("38000"))
        assertTrue(driverSlip.contains("TOTAL BAGS:"))
        assertTrue(driverSlip.contains("100 BAGS"))
        assertTrue(driverSlip.contains("5.00 MT"))

        // EscPos bytes generation with driver pass options
        val rawBytes = EscPosSlipGenerator.generateEscPosBytes(tx, 80, driverPassOptions)
        val rawText = String(rawBytes, Charsets.US_ASCII)
        assertFalse(rawText.contains("TOTAL VALUE:"))
        assertTrue(rawText.contains("TOTAL BAGS:"))
    }
}

