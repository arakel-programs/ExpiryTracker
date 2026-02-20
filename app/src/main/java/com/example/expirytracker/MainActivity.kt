package com.example.expirytracker

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.expirytracker.data.Product
import com.example.expirytracker.data.ProductStore
import com.example.expirytracker.notify.ExpiryScheduler
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.res.painterResource

class MainActivity : ComponentActivity() {

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // must be BEFORE super
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { AppScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val ctx = LocalContext.current
    val store = remember { ProductStore(ctx) }

    var search by remember { mutableStateOf("") }

    // stable filter keys
    val FILTER_ALL = "ALL"
    val FILTER_EXPIRED = "EXPIRED"
    val FILTER_TODAY = "TODAY"
    val FILTER_NEXT2 = "NEXT2"
    val FILTER_NEXT7 = "NEXT7"

    var filter by remember { mutableStateOf(FILTER_ALL) }
    var filterMenu by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("10") }
    var expDays by remember { mutableStateOf("10") }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var batchDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }

    var tab by remember { mutableStateOf(0) } // 0=Active, 1=History
    var products by remember { mutableStateOf(store.getActive()) }

    var activeCount by remember { mutableStateOf(store.getActive().size) }
    var historyCount by remember { mutableStateOf(store.getHistory().size) }

    var dialogMode by remember { mutableStateOf<String?>(null) } // SOLD, REMOVED, ADJUST, RESTORE
    var dialogProduct by remember { mutableStateOf<Product?>(null) }

    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    fun refresh() {
        val active = store.getActive()
        val history = store.getHistory()

        activeCount = active.size
        historyCount = history.size

        val base = if (tab == 0) active else history

        val filtered = base.filter { p ->
            val left = daysUntil(p.expiresAtMillis)
            when (filter) {
                FILTER_EXPIRED -> left < 0
                FILTER_TODAY -> left == 0L
                FILTER_NEXT2 -> left in 0..2
                FILTER_NEXT7 -> left in 0..7
                else -> true
            }
        }

        val q = search.trim().lowercase()
        products = if (q.isBlank()) filtered else filtered.filter { it.name.lowercase().contains(q) }
    }

    LaunchedEffect(tab, search, filter) { refresh() }

    val fmtBatch = remember { SimpleDateFormat("dd.MM", Locale.getDefault()) }
    val fmtExp = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.logo), // <-- your drawable name
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(28.dp)
                    )
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tabs
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("${stringResource(R.string.tab_active)} ($activeCount)") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("${stringResource(R.string.tab_history)} ($historyCount)") }
                )
            }

            // Search
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.search_product)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (search.isNotBlank()) {
                        TextButton(onClick = { search = "" }) { Text(stringResource(R.string.clear)) }
                    }
                }
            )

            // Filter label
            val filterLabel = when (filter) {
                FILTER_EXPIRED -> stringResource(R.string.filter_expired)
                FILTER_TODAY -> stringResource(R.string.filter_today)
                FILTER_NEXT2 -> stringResource(R.string.filter_next_2)
                FILTER_NEXT7 -> stringResource(R.string.filter_next_7)
                else -> stringResource(R.string.filter_all)
            }

            // Filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { filterMenu = true },
                    modifier = Modifier.weight(1f)
                ) { Text("${stringResource(R.string.filter)}: $filterLabel") }

                DropdownMenu(
                    expanded = filterMenu,
                    onDismissRequest = { filterMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filter_all)) },
                        onClick = { filter = FILTER_ALL; filterMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filter_expired)) },
                        onClick = { filter = FILTER_EXPIRED; filterMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filter_today)) },
                        onClick = { filter = FILTER_TODAY; filterMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filter_next_2)) },
                        onClick = { filter = FILTER_NEXT2; filterMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filter_next_7)) },
                        onClick = { filter = FILTER_NEXT7; filterMenu = false }
                    )
                }
            }

            // Add form ONLY in Active tab
            if (tab == 0) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.product_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.quantity)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = expDays,
                        onValueChange = { expDays = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.expiration_days)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val d = expDays.toIntOrNull() ?: 0
                            val base = batchDate ?: LocalDate.now()
                            selectedDate = base.plusDays(d.toLong())
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.use_days_to_date)) }

                    Button(
                        onClick = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(
                                ctx,
                                { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d) },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.pick_exp_date)) }
                }

                Text("${stringResource(R.string.expiration_date)} ${selectedDate ?: "-"}")

                Button(
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            ctx,
                            { _, y, m, d -> batchDate = LocalDate.of(y, m + 1, d) },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                ) { Text(stringResource(R.string.pick_batch_date)) }

                Text("${stringResource(R.string.batch_date)} ${batchDate ?: "-"}")

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val q = qty.toIntOrNull() ?: return@Button
                        val exp = selectedDate ?: return@Button
                        val cleanName = name.trim()
                        if (cleanName.isBlank() || q <= 0) return@Button

                        val zone = ZoneId.systemDefault()
                        val expiresAtMillis = exp.atStartOfDay(zone).toInstant().toEpochMilli()

                        val bd = batchDate ?: LocalDate.now()
                        val batchMillis = bd.atStartOfDay(zone).toInstant().toEpochMilli()

                        val id = System.currentTimeMillis() + Random.nextInt(0, 999)

                        val p = Product(
                            id = id,
                            name = cleanName,
                            batchDateMillis = batchMillis,
                            qtyInitial = q,
                            qtyCurrent = q,
                            expiresAtMillis = expiresAtMillis,
                            status = "ACTIVE"
                        )

                        store.upsert(p)
                        ExpiryScheduler.scheduleTwoAlerts(ctx, id, expiresAtMillis)

                        name = ""
                        qty = "10"
                        expDays = "10"
                        selectedDate = null
                        batchDate = LocalDate.now()
                        refresh()
                    }
                ) { Text(stringResource(R.string.add_product_schedule)) }

                Divider()
            }

            // List (Active or History)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                if (tab == 0) {
                    // GROUPED ACTIVE VIEW
                    val groups = products.groupBy { it.name.trim() }
                        .toSortedMap(String.CASE_INSENSITIVE_ORDER)

                    items(groups.entries.toList()) { entry ->
                        val groupName = entry.key
                        val itemsList = entry.value.sortedBy { it.expiresAtMillis }

                        val totalQty = itemsList.sumOf { it.qtyCurrent }
                        val nearest = itemsList.firstOrNull()
                        val nearestLeft = nearest?.let { daysUntil(it.expiresAtMillis) } ?: 0L

                        val leftText = expiryTextFromDays(nearestLeft)

                        val leftColor = when {
                            nearestLeft < 0 -> MaterialTheme.colorScheme.outline
                            nearestLeft == 0L -> MaterialTheme.colorScheme.error
                            nearestLeft in 1..2 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }

                        val isOpen = expandedGroups[groupName] ?: false

                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(groupName, style = MaterialTheme.typography.titleLarge)
                                        Text(
                                            "${stringResource(R.string.total_qty)} $totalQty",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(leftText, color = leftColor, style = MaterialTheme.typography.labelLarge)
                                    }

                                    TextButton(onClick = { expandedGroups[groupName] = !isOpen }) {
                                        Text(if (isOpen) stringResource(R.string.hide) else stringResource(R.string.view))
                                    }
                                }

                                if (isOpen) {
                                    Divider()

                                    itemsList.forEach { p ->
                                        val batchText = fmtBatch.format(Date(p.batchDateMillis))
                                        val expText = fmtExp.format(Date(p.expiresAtMillis))

                                        val left = daysUntil(p.expiresAtMillis)

                                        val bLeftText = expiryTextFromDays(left)

                                        val bLeftColor = when {
                                            left < 0 -> MaterialTheme.colorScheme.outline
                                            left == 0L -> MaterialTheme.colorScheme.error
                                            left in 1..2 -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.primary
                                        }

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(
                                                            "${stringResource(R.string.batch_label)} $batchText  •  ${stringResource(R.string.expires_label)} $expText",
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        Text(
                                                            bLeftText,
                                                            color = bLeftColor,
                                                            style = MaterialTheme.typography.labelMedium
                                                        )
                                                    }
                                                    Text(
                                                        "${stringResource(R.string.qty)} ${p.qtyCurrent}",
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(onClick = { dialogMode = "SOLD"; dialogProduct = p }) {
                                                        Text(stringResource(R.string.sold))
                                                    }
                                                    Button(onClick = { dialogMode = "REMOVED"; dialogProduct = p }) {
                                                        Text(stringResource(R.string.removed))
                                                    }
                                                    OutlinedButton(onClick = { dialogMode = "ADJUST"; dialogProduct = p }) {
                                                        Text(stringResource(R.string.adjust))
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }

                } else {
                    // HISTORY VIEW (per item)
                    items(products) { p ->
                        val batchText = fmtBatch.format(Date(p.batchDateMillis))
                        val expText = fmtExp.format(Date(p.expiresAtMillis))

                        val left = daysUntil(p.expiresAtMillis)
                        val leftText = expiryTextFromDays(left)

                        val leftColor = when {
                            left < 0 -> MaterialTheme.colorScheme.outline
                            left == 0L -> MaterialTheme.colorScheme.error
                            left in 1..2 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.name, style = MaterialTheme.typography.titleLarge)
                                        Text(
                                            "${stringResource(R.string.batch_label)} $batchText  •  ${stringResource(R.string.expires_label)} $expText",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(leftText, color = leftColor, style = MaterialTheme.typography.labelLarge)
                                    }
                                    StatusChip(p.status)
                                }

                                Text(
                                    "${stringResource(R.string.qty)} ${p.qtyCurrent}/${p.qtyInitial}",
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { dialogMode = "RESTORE"; dialogProduct = p }) {
                                        Text(stringResource(R.string.restore))
                                    }

                                    OutlinedButton(onClick = {
                                        ExpiryScheduler.cancelAlerts(ctx, p.id)
                                        store.deleteById(p.id)
                                        refresh()
                                    }) { Text(stringResource(R.string.delete)) }
                                }
                            }
                        }
                    }
                }
            }

            // Dialogs
            val dp = dialogProduct
            val mode = dialogMode

            if (dp != null && mode != null) {
                when (mode) {
                    "SOLD" -> NumberInputDialog(
                        title = stringResource(R.string.dialog_sold_title),
                        label = stringResource(R.string.dialog_sold_label),
                        confirmText = stringResource(R.string.dialog_apply),
                        onDismiss = { dialogMode = null; dialogProduct = null },
                        onConfirm = { n ->
                            val sold = n.coerceAtLeast(0)
                            val newQty = (dp.qtyCurrent - sold).coerceAtLeast(0)
                            val newStatus = if (newQty == 0) "SOLD_OUT" else dp.status
                            store.updateQty(dp.id, newQty, newStatus)
                            if (newStatus != "ACTIVE") ExpiryScheduler.cancelAlerts(ctx, dp.id)
                            dialogMode = null; dialogProduct = null
                            refresh()
                        }
                    )

                    "REMOVED" -> NumberInputDialog(
                        title = stringResource(R.string.dialog_removed_title),
                        label = stringResource(R.string.dialog_removed_label),
                        confirmText = stringResource(R.string.dialog_apply),
                        onDismiss = { dialogMode = null; dialogProduct = null },
                        onConfirm = { n ->
                            val removed = n.coerceAtLeast(0)
                            val newQty = (dp.qtyCurrent - removed).coerceAtLeast(0)
                            val newStatus = if (newQty == 0) "SOLD_OUT" else dp.status
                            store.updateQty(dp.id, newQty, newStatus)
                            if (newStatus != "ACTIVE") ExpiryScheduler.cancelAlerts(ctx, dp.id)
                            dialogMode = null; dialogProduct = null
                            refresh()
                        }
                    )

                    "ADJUST" -> NumberInputDialog(
                        title = stringResource(R.string.dialog_adjust_title),
                        label = stringResource(R.string.dialog_adjust_label),
                        confirmText = stringResource(R.string.dialog_set),
                        onDismiss = { dialogMode = null; dialogProduct = null },
                        onConfirm = { n ->
                            val newQty = n.coerceAtLeast(0)
                            val newStatus = if (newQty == 0) "SOLD_OUT" else dp.status
                            store.updateQty(dp.id, newQty, newStatus)
                            if (newStatus != "ACTIVE") ExpiryScheduler.cancelAlerts(ctx, dp.id)
                            dialogMode = null; dialogProduct = null
                            refresh()
                        }
                    )

                    "RESTORE" -> NumberInputDialog(
                        title = stringResource(R.string.dialog_restore_title),
                        label = stringResource(R.string.dialog_restore_label),
                        confirmText = stringResource(R.string.dialog_restore_btn),
                        onDismiss = { dialogMode = null; dialogProduct = null },
                        onConfirm = { n ->
                            val restoredQty = n.coerceAtLeast(0)
                            val newStatus = if (restoredQty == 0) "SOLD_OUT" else "ACTIVE"

                            store.setStatusAndQty(dp.id, restoredQty, newStatus)

                            if (newStatus == "ACTIVE") {
                                ExpiryScheduler.scheduleTwoAlerts(ctx, dp.id, dp.expiresAtMillis)
                            } else {
                                ExpiryScheduler.cancelAlerts(ctx, dp.id)
                            }

                            dialogMode = null
                            dialogProduct = null
                            refresh()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NumberInputDialog(
    title: String,
    label: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var valueText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = valueText,
                onValueChange = { valueText = it.filter { ch -> ch.isDigit() } },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = valueText.toIntOrNull() ?: 0
                    onConfirm(n)
                }
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

fun daysUntil(expMillis: Long): Long {
    val today = LocalDate.now()
    val expDate = Instant.ofEpochMilli(expMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return ChronoUnit.DAYS.between(today, expDate)
}

@Composable
fun expiryTextFromDays(daysLeft: Long): String {
    return when {
        daysLeft < 0 -> pluralStringResource(
            id = R.plurals.expired_days_ago,
            count = kotlin.math.abs(daysLeft).toInt(),
            kotlin.math.abs(daysLeft).toInt()
        )

        daysLeft == 0L -> stringResource(R.string.expires_today)

        else -> pluralStringResource(
            id = R.plurals.expires_in_days,
            count = daysLeft.toInt(),
            daysLeft.toInt()
        )
    }
}

@Composable
fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "ACTIVE" -> stringResource(R.string.status_active) to MaterialTheme.colorScheme.primary
        "SOLD_OUT" -> stringResource(R.string.status_sold_out) to MaterialTheme.colorScheme.outline
        "REMOVED" -> stringResource(R.string.status_removed) to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.outline
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}
