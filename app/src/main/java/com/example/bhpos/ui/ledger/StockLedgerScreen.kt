package com.example.bhpos.ui.ledger

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.bhpos.domain.model.TransactionType
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
import com.example.bhpos.ui.theme.MonospaceReceiptStyle
import com.example.bhpos.ui.theme.SafetyAmber
import com.example.bhpos.ui.theme.SafetyAmberContainer
import com.example.bhpos.ui.theme.SafetyAmberDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StockLedgerScreen(
    repository: CementStockRepository,
    onNavigateBack: () -> Unit,
    onNavigateToPrintSlip: (String) -> Unit
) {
    val transactions by repository.getTransactionsFlow().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<TransactionType?>(null) }

    val filteredList = transactions.filter { tx ->
        val matchesQuery = searchQuery.isBlank() ||
                tx.slipNo.contains(searchQuery, ignoreCase = true) ||
                tx.partyName.contains(searchQuery, ignoreCase = true) ||
                tx.vehicleNo.contains(searchQuery, ignoreCase = true)

        val matchesType = filterType == null || tx.type == filterType

        matchesQuery && matchesType
    }

    val totalInwardBags = transactions.filter { it.type == TransactionType.INWARD }.sumOf { it.totalBags }
    val totalOutwardBags = transactions.filter { it.type == TransactionType.OUTWARD }.sumOf { it.totalBags }
    val netBags = totalInwardBags - totalOutwardBags

    Scaffold(
        topBar = {
            Column {
                CementTrackHeader(
                    currentScreenTitle = "Stock Ledger",
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
                            text = "GODOWN AUDIT LEDGER",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        containerColor = ConcreteCanvas
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Input
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Slip #, Truck #, Dealer...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // Daily Movement Summary Telemetry Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ConcreteBorder, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = ConcreteSurfaceCard),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("DAY MOVEMENT SUMMARY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(color = ConcreteSurfaceContainerLow, shape = RoundedCornerShape(4.dp)) {
                                Text("SHIFT: DAY-01", style = MonospaceReceiptStyle.copy(fontSize = 10.sp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp), color = ConcreteSurfaceContainerLow) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = EmeraldInward, modifier = Modifier.size(12.dp))
                                        Text("INWARD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldInward)
                                    }
                                    Text("+$totalInwardBags", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EmeraldInward)
                                    Text(String.format(Locale.ENGLISH, "%.1f MT", (totalInwardBags * 50.0) / 1000.0), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp), color = ConcreteSurfaceContainerLow) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = CrimsonOutward, modifier = Modifier.size(12.dp))
                                        Text("OUTWARD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CrimsonOutward)
                                    }
                                    Text("-$totalOutwardBags", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CrimsonOutward)
                                    Text(String.format(Locale.ENGLISH, "%.1f MT", (totalOutwardBags * 50.0) / 1000.0), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp), color = SafetyAmberContainer) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("NET TALLY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SafetyAmberDark)
                                    Text(if (netBags >= 0) "+$netBags" else "$netBags", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SafetyAmberDark)
                                    Text("Bags on floor", fontSize = 10.sp, color = SafetyAmberDark)
                                }
                            }
                        }
                    }
                }
            }

            // Filter Tabs
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            label = "All Movements (${transactions.size})",
                            isSelected = filterType == null,
                            onClick = { filterType = null }
                        )
                    }
                    item {
                        FilterChip(
                            label = "Inward (+)",
                            isSelected = filterType == TransactionType.INWARD,
                            onClick = { filterType = TransactionType.INWARD }
                        )
                    }
                    item {
                        FilterChip(
                            label = "Outward (-)",
                            isSelected = filterType == TransactionType.OUTWARD,
                            onClick = { filterType = TransactionType.OUTWARD }
                        )
                    }
                }
            }

            // Transactions Listing
            items(filteredList) { tx ->
                LedgerItemCard(
                    tx = tx,
                    onReprint = { onNavigateToPrintSlip(tx.slipNo) }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = if (isSelected) SafetyAmber else ConcreteSurfaceCard,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, ConcreteBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun LedgerItemCard(
    tx: StockTransaction,
    onReprint: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ConcreteBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = ConcreteSurfaceCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tx.slipNo,
                        style = MonospaceReceiptStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    Surface(
                        color = if (tx.type == TransactionType.INWARD) EmeraldInwardFixed else CrimsonOutward.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = tx.type.name,
                            color = if (tx.type == TransactionType.INWARD) EmeraldInwardOnFixed else CrimsonOutward,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = dateFormat.format(Date(tx.timestamp)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = tx.partyName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Truck: ${tx.vehicleNo}",
                    style = MonospaceReceiptStyle.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${tx.totalBags} Bags (${String.format(Locale.ENGLISH, "%.2f MT", tx.totalMetricTons)})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (tx.type == TransactionType.INWARD) EmeraldInward else CrimsonOutward
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.ENGLISH, "INR %,.2f", tx.totalAmount),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SafetyAmberDark
                )

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onReprint() },
                    color = ConcreteSurfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(12.dp))
                        Text("VIEW / PRINT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
