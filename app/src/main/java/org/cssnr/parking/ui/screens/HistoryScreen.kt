package org.cssnr.parking.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.cssnr.parking.R
import org.cssnr.parking.data.db.History
import org.cssnr.parking.ui.theme.ParKingTheme
import org.cssnr.parking.ui.viewmodel.HistoryViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel = viewModel(),
    onRecordClick: (History) -> Unit = {},
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<History?>(null) }

    HistoryScreen(
        history = history,
        onRecordClick = onRecordClick,
        onDeleteRequest = { deleteTarget = it },
    )

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Record") },
            text = { Text("Delete this parking record?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(record.id)
                        deleteTarget = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<History>,
    onRecordClick: (History) -> Unit,
    onDeleteRequest: (History) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("History") }) },
    ) { innerPadding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No parking history yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Disconnect your car's Bluetooth device to record a parking spot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history, key = { it.id }) { record ->
                    HistoryRow(
                        record = record,
                        onClick = { onRecordClick(record) },
                        onDelete = { onDeleteRequest(record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    record: History,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (record.bluetoothAddress.isBlank()) {
                Icon(
                    painter = painterResource(R.drawable.md_pin_road_24px),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = record.bluetoothName ?: record.bluetoothAddress,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (record.bluetoothAddress.isNotBlank()) {
                    Text(
                        text = record.bluetoothAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatLocation(record),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatTimestamp(record.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                )
            }
        }
    }
}

private fun formatLocation(record: History): String =
    String.format(
        Locale.US,
        "%.5f, %.5f",
        record.latitude,
        record.longitude,
    )

private fun formatTimestamp(timestamp: Long): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    ParKingTheme {
        HistoryScreen(
            history = listOf(
                History(
                    timestamp = System.currentTimeMillis(),
                    latitude = 47.7289295,
                    longitude = -122.3484597,
                    bluetoothAddress = "20:21:A7:E0:02:D5",
                    bluetoothName = "My Car",
                ),
            ),
            onRecordClick = {},
            onDeleteRequest = {},
        )
    }
}