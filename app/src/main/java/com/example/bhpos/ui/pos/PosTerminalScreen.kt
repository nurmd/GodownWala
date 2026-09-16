package com.example.bhpos.ui.pos

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingBag
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.bhpos.domain.model.CementProduct
import com.example.bhpos.domain.model.Party
import com.example.bhpos.domain.model.StockTransaction
import com.example.bhpos.domain.model.StockTransactionItem
import com.example.bhpos.domain.model.TransactionType
import com.example.bhpos.ui.components.CementTrackHeader
import com.example.bhpos.ui.theme.ConcreteBorder
import com.example.bhpos.ui.theme.ConcreteCanvas
import com.example.bhpos.ui.theme.ConcreteSurfaceCard
import com.example.bhpos.ui.theme.ConcreteSurfaceContainer
import com.example.bhpos.ui.theme.ConcreteSurfaceContainerHigh
import com.example.bhpos.ui.theme.EmeraldInwardFixed
import com.example.bhpos.ui.theme.EmeraldInwardOnFixed
import com.example.bhpos.ui.theme.MonospaceReceiptStyle
import com.example.bhpos.ui.theme.SafetyAmber
import com.example.bhpos.ui.theme.SafetyAmberContainer
import com.example.bhpos.ui.theme.SafetyAmberDark
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PosTerminalScreen(
    repository: CementStockRepository,
    onNavigateBack: () -> Unit,
    onSlipCreated: (String) -> Unit
) {
    val products by repository.getProductsFlow().collectAsState(initial = emptyList())
    val parties = remember { repository.getParties() }
    var selectedPartyIndex by remember { mutableStateOf(0) }
    val selectedParty = parties.getOrElse(selectedPartyIndex) { parties.first() }

    var selectedCategory by remember { mutableStateOf("all") }
    val cart = remember { mutableStateMapOf<String, Int>() } // ProductId -> QuantityBags
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf(
        "all" to "All Cement",
        "ppc" to "PPC Blended",
        "opc" to "OPC 53G",
        "specialty" to "Specialty / White"
    )

    val filteredProducts = if (selectedCategory == "all") {
        products
    } else {
        products.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    val totalBags = cart.values.sum()
    val totalMt = (totalBags * 50.0) / 1000.0
    val totalAmount = cart.entries.sumOf { (prodId, qty) ->
        val prod = products.find { it.id == prodId }
        (prod?.defaultRatePerBag ?: 0.0) * qty
    }

    Scaffold(
        topBar = {
            Column {
                CementTrackHeader(
                    currentScreenTitle = "POS Terminal",
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
                            text = "FAST DISPATCH TERMINAL",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Cart Bottom Bar (Sticky Anchorage min 88px)
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
                    errorMessage?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TOTAL DISPATCH TALLY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.Baseline,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "$totalBags Bags",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(Locale.ENGLISH, "(%.2f MT)", totalMt),
                                    style = MonospaceReceiptStyle.copy(fontSize = 13.sp),
                                    color = SafetyAmberDark
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "EST. AMOUNT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(Locale.ENGLISH, "₹%,.2f", totalAmount),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (totalBags == 0) {
                                errorMessage = "Please select at least 1 bag"
                                return@Button
                            }
                            errorMessage = null
                            scope.launch {
                                val slipNo = "GP-${(1000..9999).random()}"
                                val items = cart.mapNotNull { (prodId, qty) ->
                                    val prod = products.find { it.id == prodId } ?: return@mapNotNull null
                                    StockTransactionItem(
                                        productId = prod.id,
                                        productName = prod.name,
                                        quantityBags = qty,
                                        metricTons = (qty * 50.0) / 1000.0,
                                        ratePerBag = prod.defaultRatePerBag,
                                        batchNo = prod.batchNo,
                                        bayLocation = prod.bayLocation
                                    )
                                }

                                val tx = StockTransaction(
                                    id = "tx_${System.currentTimeMillis()}",
                                    slipNo = slipNo,
                                    type = TransactionType.OUTWARD,
                                    timestamp = System.currentTimeMillis(),
                                    partyName = selectedParty.name,
                                    vehicleNo = "MH-12-QZ-4891",
                                    driverName = "Rameshwar",
                                    driverPhone = "+91 98234-11029",
                                    challanNo = "DC-2024-${(1000..9999).random()}",
                                    ewbNo = "8921-4402-${(1000..9999).random()}",
                                    destinationSite = selectedParty.defaultDestination,
                                    totalBags = totalBags,
                                    totalMetricTons = totalMt,
                                    totalAmount = totalAmount,
                                    items = items
                                )

                                val result = repository.recordDispatch(tx)
                                result.onSuccess {
                                    cart.clear()
                                    onSlipCreated(slipNo)
                                }.onFailure { error ->
                                    errorMessage = error.message
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PRINT GATE PASS ($totalBags BAGS)",
                            fontSize = 14.sp,
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
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Category Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                items(categories) { (catKey, catLabel) ->
                    val isSelected = selectedCategory == catKey
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedCategory = catKey },
                        color = if (isSelected) SafetyAmber else ConcreteSurfaceCard,
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, ConcreteBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = catLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Party / Consignee Selector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ConcreteBorder, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = ConcreteSurfaceCard),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = SafetyAmberDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "PARTY / CONSIGNEE:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedParty.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                selectedPartyIndex = (selectedPartyIndex + 1) % parties.size
                            },
                        color = ConcreteSurfaceContainer
                    ) {
                        Text(
                            text = "CHANGE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SafetyAmberDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Products Grid (2 Columns)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredProducts) { product ->
                    val inCart = cart[product.id] ?: 0
                    PosProductTile(
                        product = product,
                        cartCount = inCart,
                        onAddBags = { countToAdd ->
                            val current = cart[product.id] ?: 0
                            val target = current + countToAdd
                            if (target <= product.currentStockBags) {
                                cart[product.id] = target
                                errorMessage = null
                            } else {
                                errorMessage = "Only ${product.currentStockBags} bags available for ${product.name}"
                            }
                        },
                        onClear = {
                            cart.remove(product.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PosProductTile(
    product: CementProduct,
    cartCount: Int,
    onAddBags: (Int) -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (cartCount > 0) 2.dp else 1.dp,
                color = if (cartCount > 0) SafetyAmber else ConcreteBorder,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = ConcreteSurfaceCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand monogram
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SafetyAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = product.brandName.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = SafetyAmberDark
                    )
                }

                if (cartCount > 0) {
                    Surface(
                        color = SafetyAmber,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "$cartCount in cart",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Surface(
                color = EmeraldInwardFixed,
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    text = "${product.grade} • ${product.bayLocation}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldInwardOnFixed,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            Text(
                text = product.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Baseline
            ) {
                Text(
                    text = "₹${product.defaultRatePerBag.toInt()}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SafetyAmberDark
                )
                Text(
                    text = "${product.currentStockBags} Stk",
                    style = MonospaceReceiptStyle.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick increment chips: +1, +10, +50
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onAddBags(1) },
                    color = ConcreteSurfaceContainerHigh
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+1", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onAddBags(10) },
                    color = ConcreteSurfaceContainerHigh
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+10", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onAddBags(50) },
                    color = SafetyAmberContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+50", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SafetyAmberDark)
                    }
                }
            }
        }
    }
}
