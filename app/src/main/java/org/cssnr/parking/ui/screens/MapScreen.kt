package org.cssnr.parking.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import org.cssnr.parking.ui.AutomaticTrackingHeader
import org.cssnr.parking.ui.AutomaticTrackingStatus
import org.cssnr.parking.ui.navigation.MapDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MapRoute(
    detail: MapDetail?,
    title: String,
    onBack: (() -> Unit)?,
    trackingStatus: AutomaticTrackingStatus? = null,
    onTrackingStatusClick: () -> Unit = {},
) {
    val locationProvider = rememberDefaultLocationProvider()
    val locationState = rememberLocationState(provider = locationProvider)

    LaunchedEffect(locationState.permission) {
        val permission = locationState.permission
        if (permission is LocationPermission.NotGranted && permission.canRequest != false) {
            locationState.requestPermission()
        }
    }

    MapScreen(
        detail = detail,
        title = title,
        userPosition = locationState.lastLocation?.position,
        onBack = onBack,
        trackingStatus = trackingStatus,
        onTrackingStatusClick = onTrackingStatusClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    detail: MapDetail?,
    title: String,
    userPosition: Position?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trackingStatus: AutomaticTrackingStatus? = null,
    onTrackingStatusClick: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (detail != null) {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatSubtitle(detail),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    AutomaticTrackingHeader(
                        status = trackingStatus,
                        onClick = onTrackingStatusClick,
                    )
                },
            )
        },
    ) { innerPadding ->
        val parkingPosition = detail?.let { position ->
            Position(
                longitude = position.longitude,
                latitude = position.latitude,
            )
        }
        val mapState = rememberMapState(
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
            initialCameraPosition = parkingPosition?.let {
                CameraPosition(target = it, zoom = 17.0)
            } ?: CameraPosition(),
        ) {
            if (parkingPosition != null) {
                val parkingSource = rememberGeoJsonSource(
                    GeoJsonData.JsonString(pointFeatureJson(parkingPosition)),
                )
                CircleLayer(
                    id = "parking",
                    source = parkingSource,
                    color = const(Color(0xFFD32F2F)),
                    radius = const(7.dp),
                    strokeColor = const(Color.White),
                    strokeWidth = const(2.dp),
                )
            }

            if (userPosition != null) {
                val userSource = rememberGeoJsonSource(
                    GeoJsonData.JsonString(pointFeatureJson(userPosition)),
                )
                CircleLayer(
                    id = "user",
                    source = userSource,
                    color = const(Color(0xFF1976D2)),
                    radius = const(5.dp),
                    strokeColor = const(Color.White),
                    strokeWidth = const(2.dp),
                )
            }
        }

        LaunchedEffect(parkingPosition) {
            val target = parkingPosition ?: return@LaunchedEffect
            mapState.setCameraPosition(CameraPosition(target = target, zoom = 17.0))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            MaplibreMap(
                modifier = Modifier.fillMaxSize(),
                state = mapState,
            )
        }
    }
}

private fun pointFeatureJson(position: Position): String =
    """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${position.longitude},${position.latitude}]}}]}"""

/**
 * The second title bar line: when the record was saved, then the device that
 * triggered it. [MapDetail.device] falls back to the bluetooth address and manual
 * saves are labelled "Manually Parked", so it is never empty and there is no
 * dangling separator case to handle.
 */
private fun formatSubtitle(detail: MapDetail): String =
    "${formatTimestamp(detail.timestamp)} - ${detail.device}"

private fun formatTimestamp(timestamp: Long): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))