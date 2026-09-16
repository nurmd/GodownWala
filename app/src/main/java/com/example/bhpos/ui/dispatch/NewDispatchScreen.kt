package com.example.bhpos.ui.dispatch

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CorporateFare
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bhpos.data.repository.CementStockRepository
import com.example.bhpos.domain.model.StockTransaction
import com.example.bhpos.domain.model.StockTransactionItem
import com.example.bhpos.domain.model.TransactionType
import com.example.bhpos.ui.components.CementTrackHeader
import com.example.bhpos.ui.theme.ConcreteBorder
import com.example.bhpos.ui.theme.ConcreteCanvas
import com.example.bhpos.ui.theme.ConcreteSurfaceCard
import com.example.bhpos.ui.theme.ConcreteSurfaceContainer
import com.example.bhpos.ui.theme.ConcreteSurfaceContainerLow
import com.example.bhpos.ui.theme.EmeraldInward
import com.example.bhpos.ui.theme.EmeraldInwardFixed
import com.example.bhpos.ui.theme.EmeraldInwardOnFixed
import com.example.bhpos.ui.theme.MonospaceReceiptStyle
import com.example.bhpos.ui.theme.SafetyAmber
import com.example.bhpos.ui.theme.SafetyAmberDark
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun NewDispatchScreen(
    repository: CementStockRepository,
    onNavigateBack: () -> Unit,
    onSlipIssued: (String) -> Unit
) {
    val products by repository.getProductsFlow().collectAsState(initial = emptyList())
    val parties = remember { repository.getParties() }
    var selectedPartyIndex by remember { mutableIntStateOf(0) }
    val party = parties.getOrElse(selectedPartyIndex) { parties.first() }

    var selectedProductIndex by remember { mutableIntStateOf(0) }
    val product = products.getOrNull(selectedProductIndex)

    var dispatchQuantityBags by remember { mutableIntStateOf(200) }
    var vehicleNo by remember { mutableStateOf("MH-12-QZ-4891") }
    var driverName by remember { mutableStateOf("Rameshwar") }
    var driverPhone by remember { mutableStateOf("+91 98234-11029") }
    var challanNo by remember { mutableStateOf("DC-2024-8841") }
    var ewbNo by remember { mutableStateOf("8921-4402-9912") }
    var destinationSite by remember { mutableStateOf(party.defaultDestination) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val tonnage = (dispatchQuantityBags * 50.0) / 1000.0
    val totalVal = (product?.defaultRatePerBag ?: 0.0) * dispatchQuantityBags

    Scaffold(
        topBar = {
            Column {
                CementTrackHeader(
                    currentScreenTitle = "New Dispatch",
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
                            text = "CREATE OUTWARD DISPATCH",
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
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (product == null) return@Button
                            if (dispatchQuantityBags <= 0) {
                                errorMessage = "Dispatch quantity must be greater than 0"
                                return@Button
                            }
                            if (dispatchQuantityBags > product.currentStockBags) {
                                errorMessage = "Cannot dispatch more than available stock (${product.currentStockBags} bags)"
                                return@Button
                            }

                            errorMessage = null
                            scope.launch {
                                val slipNo = "GP-${(1000..9999).random()}"
                                val tx = StockTransaction(
                                    id = "tx_${System.currentTimeMillis()}",
                                    slipNo = slipNo,
                                    type = TransactionType.OUTWARD,
                                    timestamp = System.currentTimeMillis(),
                                    partyName = party.name,
                                    vehicleNo = vehicleNo,
                                    driverName = driverName,
                                    driverPhone = driverPhone,
                                    challanNo = challanNo,
                                    ewbNo = ewbNo,
                                    destinationSite = destinationSite,
                                    totalBags = dispatchQuantityBags,
                                    totalMetricTons = tonnage,
                                    totalAmount = totalVal,
                                    items = listOf(
                                        StockTransactionItem(
                                            productId = product.id,
                                            productName = product.name,
                                            quantityBags = dispatchQuantityBags,
                                            metricTons = tonnage,
                                            ratePerBag = product.defaultRatePerBag,
                                            batchNo = product.batchNo,
                                            bayLocation = product.bayLocation
                                        )
                                    )
                                )

                                val res = repository.recordDispatch(tx)
                                res.onSuccess {
                                    onSlipIssued(slipNo)
                                }.onFailure {
                                    errorMessage = it.message
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ISSUE GATE PASS ($dispatchQuantityBags BAGS • ${String.format(Locale.ENGLISH, "%.2f MT", tonnage)})",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Party & Destination Card
            item {
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
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CorporateFare, contentDescription = null, tint = SafetyAmberDark)
                                Text("CONSIGNEE & SITE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Surface(color = EmeraldInwardFixed, shape = RoundedCornerShape(3.dp)) {
                                Text("GST VERIFIED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EmeraldInwardOnFixed, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }

                        // Select party
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ConcreteBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    selectedPartyIndex = (selectedPartyIndex + 1) % parties.size
                                    destinationSite = parties[selectedPartyIndex].defaultDestination
                                },
                            color = ConcreteSurfaceContainerLow,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Primary Dealer (Tap to change)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(party.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("GSTIN: ${party.gstin} • ACCT: ${party.accountNo}", style = MonospaceReceiptStyle.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        OutlinedTextField(
                            value = destinationSite,
                            onValueChange = { destinationSite = it },
                            label = { Text("Destination Unloading Site") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.PinDrop, contentDescription = null, tint = SafetyAmberDark) }
                        )
                    }
                }
            }

            // 2. Cement Product & Quantity Stepper
            item {
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
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = SafetyAmberDark)
                                Text("CEMENT ITEM & QUANTITY", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Surface(color = ConcreteSurfaceContainer, shape = RoundedCornerShape(3.dp)) {
                                Text("50 KG BAGS", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }

                        if (product != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ConcreteBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        if (products.isNotEmpty()) {
                                            selectedProductIndex = (selectedProductIndex + 1) % products.size
                                        }
                                    },
                                color = ConcreteSurfaceContainerLow,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(product.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("${product.bayLocation} • Available: ${product.currentStockBags} Bags", fontSize = 12.sp, color = EmeraldInward)
                                    }
                                    Text("TAP TO CHANGE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SafetyAmberDark)
                                }
                            }
                        }

                        // Stepper row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable {
                                        if (dispatchQuantityBags >= 50) dispatchQuantityBags -= 50
                                    },
                                color = ConcreteSurfaceContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Remove, contentDescription = "-50")
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$dispatchQuantityBags Bags",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(Locale.ENGLISH, "Net Weight: %.2f MT", tonnage),
                                    style = MonospaceReceiptStyle.copy(fontSize = 12.sp),
                                    color = SafetyAmberDark
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable {
                                        val limit = product?.currentStockBags ?: 9999
                                        if (dispatchQuantityBags + 50 <= limit) {
                                            dispatchQuantityBags += 50
                                        }
                                    },
                                color = ConcreteSurfaceContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = "+50")
                                }
                            }
                        }
                    }
                }
            }

            // 3. Transport & Driver Details
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ConcreteBorder, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = ConcreteSurfaceCard),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("TRANSPORT & VEHICLE MANIFEST", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = vehicleNo,
                                onValueChange = { vehicleNo = it },
                                label = { Text("Vehicle / Truck #") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = driverName,
                                onValueChange = { driverName = it },
                                label = { Text("Driver Name") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = driverPhone,
                                onValueChange = { driverPhone = it },
                                label = { Text("Driver Phone") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = challanNo,
                                onValueChange = { challanNo = it },
                                label = { Text("Challan #") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = ewbNo,
                            onValueChange = { ewbNo = it },
                            label = { Text("E-Way Bill (EWB) #") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
