package org.cssnr.parking.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = viewModel(),
    trackingStatus: AutomaticTrackingStatus? = null,
    onTrackingStatusClick: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
) {
    val context = LocalContext.current
    val acraInfoLink = stringResource(R.string.acra_info_link)
    val crashReporting by viewModel.crashReporting.collectAsStateWithLifecycle()

    SettingsScreen(
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
                Row {
                    TextButton(onClick = onCrashReportingMoreInfo) {
                        Text(stringResource(R.string.acra_disable_more_info))
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
            dismissButton = {
                TextButton(onClick = { showCrashReportingDialog = false }) {
                    Text(stringResource(R.string.acra_disable_cancel))
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
            crashReporting = true,
            onCrashReportingChange = {},
            onCrashReportingMoreInfo = {},
        )
    }
}