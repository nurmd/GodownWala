package com.example.bhpos.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bhpos.data.repository.CementStockRepository
import com.example.bhpos.domain.model.StockTransaction
import com.example.bhpos.printer.EscPosSlipGenerator
import com.example.bhpos.ui.components.CementTrackHeader
import com.example.bhpos.ui.theme.ConcreteBorder
import com.example.bhpos.ui.theme.ConcreteCanvas
import com.example.bhpos.ui.theme.ConcreteSurfaceCard
import com.example.bhpos.ui.theme.ConcreteSurfaceContainer
import com.example.bhpos.ui.theme.ConcreteSurfaceContainerLow
import com.example.bhpos.ui.theme.EmeraldInward
import com.example.bhpos.ui.theme.EmeraldInwardFixed
import com.example.bhpos.ui.theme.MonospaceReceiptStyle
import com.example.bhpos.ui.theme.SafetyAmber
import com.example.bhpos.ui.theme.SafetyAmberDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ThermalPrintPreviewScreen(
    repository: CementStockRepository,
    slipNo: String?,
    onNavigateBack: () -> Unit
) {
    var transaction by remember { mutableStateOf<StockTransaction?>(null) }
    var paperWidthMm by remember { mutableIntStateOf(80) } // 58 or 80
    var copies by remember { mutableIntStateOf(2) } // 1, 2, 3
    var isPrinting by remember { mutableStateOf(false) }
    var printSuccess by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(slipNo) {
        if (!slipNo.isNullOrBlank()) {
            transaction = repository.getTransactionBySlipNo(slipNo)
        }
        if (transaction == null) {
            transaction = repository.getLastSlip()
        }
    }

    val receiptContent = remember(transaction, paperWidthMm) {
        transaction?.let { EscPosSlipGenerator.generatePreviewText(it, paperWidthMm) } ?: "Loading slip details..."
    }

    Scaffold(
        topBar = {
            Column {
                CementTrackHeader(
                    currentScreenTitle = "Thermal Print",
                    godownName = "Godown #4 - Central Depot"
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ConcreteCanvas
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = "THERMAL SLIP SPOOL VISUALIZER",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ConcreteSurfaceCard,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, ConcreteBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (printSuccess) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldInward, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Slip transmitted to ESC/POS hardware ($copies copies)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldInward
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val tx = transaction ?: return@Button
                            isPrinting = true
                            printSuccess = false
                            scope.launch {
                                // Simulate hardware spooling and paper cut
                                delay(600)
                                val bytes = EscPosSlipGenerator.generateEscPosBytes(tx, paperWidthMm)
                                // In production, bytes are written to BluetoothSocket.outputStream
                                isPrinting = false
                                printSuccess = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isPrinting && transaction != null
                    ) {
                        Icon(
                            imageVector = if (isPrinting) Icons.Default.ContentCut else Icons.Default.Print,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPrinting) "CUTTING & PRINTING..." else "ISSUE GATE PASS & PRINT ($copies COPIES)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        },
        containerColor = ConcreteCanvas
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Printer & Roll settings card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ConcreteBorder, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = ConcreteSurfaceCard),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.BluetoothConnected, contentDescription = null, tint = EmeraldInward)
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("POS-80C Mobile", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(EmeraldInward))
                                }
                                Text("ESC/POS Standard • Direct Link", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Surface(color = ConcreteSurfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
                            Text("88% BAT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldInward, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    }

                    // Toggles: 58mm vs 80mm and Copies
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Paper width
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Roll Width", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ConcreteSurfaceContainer),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (paperWidthMm == 58) SafetyAmber else Color.Transparent)
                                        .clickable { paperWidthMm = 58 }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("58mm (2\")", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (paperWidthMm == 58) Color.White else MaterialTheme.colorScheme.onSurface)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (paperWidthMm == 80) SafetyAmber else Color.Transparent)
                                        .clickable { paperWidthMm = 80 }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("80mm (3\")", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (paperWidthMm == 80) Color.White else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        // Copies
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Copies Configuration", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, ConcreteBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        copies = if (copies == 1) 2 else if (copies == 2) 3 else 1
                                    },
                                color = ConcreteSurfaceCard
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when (copies) {
                                            1 -> "1x (Driver)"
                                            2 -> "2x (Driver+Gate)"
                                            else -> "3x (Driver+Gate+Audit)"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Receipt Paper Container with Monospaced layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, ConcreteBorder, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = receiptContent,
                    style = MonospaceReceiptStyle.copy(
                        fontSize = if (paperWidthMm == 58) 11.sp else 12.sp,
                        lineHeight = if (paperWidthMm == 58) 15.sp else 17.sp
                    ),
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
