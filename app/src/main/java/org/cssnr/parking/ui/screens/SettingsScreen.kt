package org.cssnr.parking.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.cssnr.parking.BuildConfig
import org.cssnr.parking.R
import org.cssnr.parking.ui.AutomaticTrackingHeader
import org.cssnr.parking.ui.AutomaticTrackingStatus
import org.cssnr.parking.ui.components.SettingsGroup
import org.cssnr.parking.ui.components.SettingsTile
import org.cssnr.parking.ui.theme.ParKingTheme
import org.cssnr.parking.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = viewModel(),
    trackingStatus: AutomaticTrackingStatus? = null,
    onTrackingStatusClick: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
) {
    val context = LocalContext.current
    val acraInfoLink = stringResource(R.string.acra_info_link)
    val historyDuration by viewModel.historyDuration.collectAsStateWithLifecycle()
    val crashReporting by viewModel.crashReporting.collectAsStateWithLifecycle()

    SettingsScreen(
        historyDuration = historyDuration,
        onHistoryDurationChange = viewModel::setHistoryDuration,
        crashReporting = crashReporting,
        onCrashReportingChange = viewModel::setCrashReporting,
        onCrashReportingMoreInfo = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, acraInfoLink.toUri())
            )
        },
        trackingStatus = trackingStatus,
        onTrackingStatusClick = onTrackingStatusClick,
        onAboutClick = onNavigateToAbout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    historyDuration: Int,
    onHistoryDurationChange: (Int) -> Unit,
    crashReporting: Boolean,
    onCrashReportingChange: (Boolean) -> Unit,
    onCrashReportingMoreInfo: () -> Unit,
    trackingStatus: AutomaticTrackingStatus? = null,
    onTrackingStatusClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
) {
    var showCrashReportingDialog by rememberSaveable { mutableStateOf(false) }
    val appName = stringResource(R.string.app_name)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                actions = {
                    AutomaticTrackingHeader(
                        status = trackingStatus,
                        onClick = onTrackingStatusClick,
                    )
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            val historyLabels = listOf(
                stringResource(R.string.history_disabled),
                stringResource(R.string.history_1_week),
                stringResource(R.string.history_2_weeks),
                stringResource(R.string.history_1_month),
                stringResource(R.string.history_3_months),
                stringResource(R.string.history_6_months),
                stringResource(R.string.history_1_year),
            )
            SettingsGroup(
                title = stringResource(R.string.settings_group_application),
                tiles = listOf(
                    SettingsTile.Custom(
                        icon = rememberVectorPainter(Icons.Filled.History),
                        title = stringResource(R.string.settings_history),
                        summary = stringResource(R.string.settings_history_summary),
                        content = {
                            HistoryDurationSlider(
                                value = historyDuration,
                                labels = historyLabels,
                                onSelect = onHistoryDurationChange,
                            )
                        },
                    ),
                ),
            )
            SettingsGroup(
                title = stringResource(R.string.settings_group_debug),
                tiles = listOf(
                    SettingsTile.Toggle(
                        icon = rememberVectorPainter(Icons.Filled.BugReport),
                        title = stringResource(R.string.settings_crash_reporting),
                        summary = stringResource(R.string.settings_crash_reporting_summary),
                        checked = crashReporting,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                onCrashReportingChange(true)
                            } else {
                                showCrashReportingDialog = true
                            }
                        },
                    ),
                ),
            )
            SettingsGroup(
                title = stringResource(R.string.settings_group_about),
                tiles = listOf(
                    SettingsTile.Link(
                        icon = rememberVectorPainter(Icons.Filled.Info),
                        title = stringResource(R.string.about_parking, appName),
                        summary = stringResource(
                            R.string.about_parking_summary,
                            appName,
                            BuildConfig.VERSION_NAME,
                        ),
                        onClick = onAboutClick,
                    ),
                ),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCrashReportingDialog) {
        AlertDialog(
            onDismissRequest = { showCrashReportingDialog = false },
            title = { Text(stringResource(R.string.acra_disable_title)) },
            text = { Text(stringResource(R.string.acra_disable_message)) },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCrashReportingMoreInfo) {
                        Text(stringResource(R.string.acra_disable_more_info))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { showCrashReportingDialog = false }) {
                        Text(stringResource(R.string.acra_disable_cancel))
                    }
                    TextButton(
                        onClick = {
                            showCrashReportingDialog = false
                            onCrashReportingChange(false)
                        }
                    ) {
                        Text(stringResource(R.string.acra_disable_confirm))
                    }
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    ParKingTheme {
        SettingsScreen(
            historyDuration = 4,
            onHistoryDurationChange = {},
            crashReporting = true,
            onCrashReportingChange = {},
            onCrashReportingMoreInfo = {},
        )
    }
}

/**
 * Discrete M3 slider with 7 stops:
 * Disabled | 1 week | 2 weeks | 1 month | 3 months | 6 months | 1 year.
 *
 * 7 stops on 0f..6f means 5 intermediate steps, which draws the notch/tick at
 * each stop. Persists only onValueChangeFinished to avoid DataStore writes
 * on every drag frame.
 */
@Composable
private fun HistoryDurationSlider(
    value: Int,
    labels: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coerced = value.coerceIn(0, labels.lastIndex)
    val valueRange = 0f..labels.lastIndex.toFloat()
    var sliderValue by remember { mutableFloatStateOf(coerced.toFloat()) }
    LaunchedEffect(coerced) {
        sliderValue = coerced.toFloat()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            valueRange = valueRange,
            steps = labels.size - 2,
            onValueChangeFinished = {
                onSelect(sliderValue.roundToInt().coerceIn(0, labels.lastIndex))
            },
        )
        Text(
            text = labels[sliderValue.roundToInt().coerceIn(0, labels.lastIndex)],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}