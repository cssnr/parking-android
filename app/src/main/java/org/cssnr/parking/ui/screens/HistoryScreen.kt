package org.cssnr.parking.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.cssnr.parking.R
import org.cssnr.parking.data.db.History
import org.cssnr.parking.ui.AutomaticTrackingHeader
import org.cssnr.parking.ui.AutomaticTrackingStatus
import org.cssnr.parking.ui.theme.ParKingTheme
import org.cssnr.parking.ui.viewmodel.HistoryViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel = viewModel(),
    trackingStatus: AutomaticTrackingStatus? = null,
    onTrackingStatusClick: () -> Unit = {},
    onRecordClick: (History) -> Unit = {},
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<History?>(null) }

    HistoryScreen(
        history = history,
        onRecordClick = onRecordClick,
        onDeleteRequest = { deleteTarget = it },
        trackingStatus = trackingStatus,
        onTrackingStatusClick = onTrackingStatusClick,
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
    trackingStatus: AutomaticTrackingStatus? = null,
    onTrackingStatusClick: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    AutomaticTrackingHeader(
                        status = trackingStatus,
                        onClick = onTrackingStatusClick,
                    )
                },
            )
        },
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
                Text(
                    text = record.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                FixDetails(record)
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

/**
 * The optional fix quality row: horizontal accuracy, altitude with its own
 * vertical accuracy, and how stale the fix was when it was recorded.
 *
 * Each value is an icon plus a label rather than a pipe delimited sentence,
 * because these are unrelated quantities and a reader scanning the list should
 * be able to pick one out without parsing the ones next to it.
 *
 * Laid out as a [FlowRow] so a wide accuracy value, a large font scale, or a long
 * age all wrap to the next line instead of running off the edge of the card.
 *
 * Altitude is height above the WGS84 reference ellipsoid, not above mean sea
 * level; the vertical accuracy beside it is what says whether it is worth
 * trusting. None of these were captured before schema version 2, so a record
 * saved by an older build renders nothing here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FixDetails(record: History) {
    val accuracy = record.accuracy
    val altitude = record.altitude
    val fixAge = record.fixAgeMillis
    if (accuracy == null && altitude == null && fixAge == null) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        accuracy?.let {
            FixDetail(
                icon = Icons.Filled.MyLocation,
                label = String.format(Locale.US, "%dm", it.roundToInt()),
                description = "Horizontal accuracy",
            )
        }
        altitude?.let {
            val label = listOfNotNull(
                String.format(Locale.US, "%.0fm", it),
                record.verticalAccuracy?.let { accuracyMeters ->
                    String.format(Locale.US, "+/-%dm", accuracyMeters.roundToInt())
                },
            ).joinToString(" ")
            FixDetail(
                icon = Icons.Filled.Height,
                label = label,
                description = "Altitude above the WGS84 ellipsoid",
            )
        }
        fixAge?.let {
            FixDetail(
                icon = Icons.Filled.Schedule,
                label = formatAge(it),
                description = "Age of the location fix when it was saved",
            )
        }
    }
}

@Composable
private fun FixDetail(
    icon: ImageVector,
    label: String,
    description: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            // The value is already in the adjacent text, so the icon itself is
            // decorative; the description keeps it announced correctly.
            contentDescription = description,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatAge(ageMillis: Long): String = when {
    ageMillis < 60_000L -> "${ageMillis / 1_000L}s old"
    ageMillis < 3_600_000L -> "${ageMillis / 60_000L}m old"
    ageMillis < 86_400_000L -> "${ageMillis / 3_600_000L}h old"
    else -> "${ageMillis / 86_400_000L}d old"
}

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