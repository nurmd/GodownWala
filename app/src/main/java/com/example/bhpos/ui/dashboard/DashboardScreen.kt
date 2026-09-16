package com.example.bhpos.ui.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bhpos.data.repository.CementStockRepository
import com.example.bhpos.domain.model.CementProduct
import com.example.bhpos.domain.model.DashboardTelemetry
import com.example.bhpos.ui.components.CementTrackHeader
import com.example.bhpos.ui.theme.ConcreteBorder
import com.example.bhpos.ui.theme.ConcreteCanvas
import com.example.bhpos.ui.theme.ConcreteSurfaceCard
import com.example.bhpos.ui.theme.ConcreteSurfaceContainer
import com.example.bhpos.ui.theme.ConcreteSurfaceContainerLow
import com.example.bhpos.ui.theme.CrimsonOutward
import com.example.bhpos.ui.theme.EmeraldInward
import com.example.bhpos.ui.theme.EmeraldInwardFixed
import com.example.bhpos.ui.theme.EmeraldInwardOnFixed
import com.example.bhpos.ui.theme.HeavySlate
import com.example.bhpos.ui.theme.MetricCounterStyle
import com.example.bhpos.ui.theme.MonospaceReceiptStyle
import com.example.bhpos.ui.theme.SafetyAmber
import com.example.bhpos.ui.theme.SafetyAmberDark
import java.util.Locale

@Composable
fun DashboardScreen(
    repository: CementStockRepository,
    onNavigateToPos: () -> Unit,
    onNavigateToDispatch: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToPrintSlip: (String) -> Unit
) {
    val telemetry by repository.getDashboardTelemetry().collectAsState(
        initial = DashboardTelemetry(
            totalStoredBags = 14850,
            totalMetricTons = 742.50,
            inwardDayBags = 1200,
            inwardDayMt = 60.0,
            dispatchedBags = 850,
            dispatchedMt = 42.5,
            pendingSlipsCount = 1,
            netTallyBags = 350,
            brandDistribution = emptyList()
        )
    )

    val products by repository.getProductsFlow().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            CementTrackHeader(
                currentScreenTitle = "Dashboard",
                godownName = "Godown #4 - Central Depot"
            )
        },
        containerColor = ConcreteCanvas
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Hub & Equipment Status Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ConcreteBorder, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = ConcreteSurfaceCard),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SafetyAmber.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warehouse,
                                        contentDescription = null,
                                        tint = SafetyAmberDark,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Bay 2 • Central Logistics",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            color = EmeraldInwardFixed,
                                            shape = RoundedCornerShape(3.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                color = EmeraldInwardOnFixed,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Godown #4 (Industrial Area Hub)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            tint = HeavySlate,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Synced 2m ago",
                                            fontSize = 11.sp,
                                            color = HeavySlate
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Printer hardware chip
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            color = ConcreteSurfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BluetoothConnected,
                                        contentDescription = null,
                                        tint = EmeraldInward,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "RP-3200 (ESC/POS 80mm)",
                                        style = MonospaceReceiptStyle.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "PAPER OK",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldInward
                                )
                            }
                        }
                    }
                }
            }

            // 2. Dual Primary Action Triggers (Min height 52px for warehouse gloved operation)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onNavigateToPos,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldInward),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "STOCK IN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Button(
                        onClick = onNavigateToDispatch,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DISPATCH SLIP",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // 3. Realtime Godown Metrics Dashboard
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ConcreteBorder, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = ConcreteSurfaceCard),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL STORED BAGS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.Baseline,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.ENGLISH, "%,d", telemetry.totalStoredBags),
                                        style = MetricCounterStyle,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Bags",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    color = EmeraldInwardFixed,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "+450 TODAY",
                                        color = EmeraldInwardOnFixed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = String.format(Locale.ENGLISH, "Net: %.2f MT", telemetry.totalMetricTons),
                                    style = MonospaceReceiptStyle.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Sub-metrics Bento Grid (3 columns)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Inward
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                color = ConcreteSurfaceContainerLow
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("INWARD (DAY)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("+1,200", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EmeraldInward)
                                    Text("2 Trucks In", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Dispatched
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                color = ConcreteSurfaceContainerLow
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("DISPATCHED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("-850", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CrimsonOutward)
                                    Text("6 Cleared", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Pending
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                color = ConcreteSurfaceContainerLow
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("NET ON FLOOR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("+350", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SafetyAmberDark)
                                    Text("Balance", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Quick Action Strip: Re-Print Buffer Slip
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, ConcreteBorder, RoundedCornerShape(8.dp))
                        .clickable { onNavigateToPrintSlip("GP-9482") },
                    color = ConcreteSurfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = SafetyAmberDark,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Slip #GP-9482 Ready in Buffer",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Sharma Infra • 200 Bags PPC (10.0 MT)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ConcreteSurfaceCard,
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "RE-PRINT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 5. Material Lots by Bay Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Material Lots by Bay",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${products.size} ACTIVE BRANDS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SafetyAmberDark
                    )
                }
            }

            // 6. Material Lots Listing
            items(products) { product ->
                MaterialLotCard(
                    product = product,
                    onTap = onNavigateToPos
                )
            }

            // Bottom Navigation Trigger Button to Ledger
            item {
                Button(
                    onClick = onNavigateToLedger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HeavySlate),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("VIEW FULL STOCK LEDGER & AUDIT TRAIL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MaterialLotCard(
    product: CementProduct,
    onTap: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ConcreteBorder, RoundedCornerShape(8.dp))
            .clickable { onTap() },
        colors = CardDefaults.cardColors(containerColor = ConcreteSurfaceCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vertical accent line
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (product.currentStockBags > 1500) EmeraldInward else SafetyAmber)
                )

                Column {
                    Text(
                        text = product.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = product.bayLocation,
                            style = MonospaceReceiptStyle.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• Batch: ${product.batchNo}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${product.currentStockBags} Bags",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format(Locale.ENGLISH, "%.2f MT", product.stockMetricTons),
                    style = MonospaceReceiptStyle.copy(fontSize = 11.sp),
                    color = EmeraldInward
                )
            }
        }
    }
}
