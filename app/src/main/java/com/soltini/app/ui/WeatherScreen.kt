package com.soltini.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.soltini.app.apis.PublicApiExecutor
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.theme.LocalMyraTokens
import com.soltini.app.weather.WeatherData
import com.soltini.app.weather.WeatherParser
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.Calendar
import kotlin.coroutines.resume

private sealed interface WeatherUi {
    data object Loading : WeatherUi
    data class Failed(val message: String) : WeatherUi
    data class Ready(val data: WeatherData) : WeatherUi
}

/**
 * Weather. Real data only: location (if the user allowed it) or a typed city -> PublicApiExecutor.fetchWeather
 * (wttr.in). If location is denied/unavailable or the request fails, an honest message is shown. It never falls
 * back to a hidden default city or sample numbers.
 */
@Composable
fun WeatherScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val t = LocalMyraTokens.current

    fun hasLocation() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var permissionGranted by remember { mutableStateOf(hasLocation()) }
    var permissionAsked by remember { mutableStateOf(false) }
    var cityQuery by remember { mutableStateOf<String?>(null) } // set when the user types a city
    var cityInput by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var ui by remember { mutableStateOf<WeatherUi>(WeatherUi.Loading) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        permissionAsked = true
        refreshKey++
    }

    LaunchedEffect(permissionGranted, cityQuery, refreshKey) {
        val city = cityQuery
        if (city == null && !permissionGranted) {
            ui = WeatherUi.Failed(
                if (permissionAsked) "Location permission was denied. Allow it, or search for a city below."
                else "Allow location to see weather for where you are, or search for a city below."
            )
            return@LaunchedEffect
        }
        ui = WeatherUi.Loading
        val place: String? = city ?: lastKnownLatLon(context)?.let { "${it.first},${it.second}" }
        if (place == null) {
            ui = WeatherUi.Failed("Couldn't determine your location. Search for a city below or try again.")
            return@LaunchedEffect
        }
        val result: JSONObject = PublicApiExecutor(context).fetchWeather(place)
        val data = result.optJSONObject("data")
        val parsed = if (result.optString("status") == "success" && data != null) {
            WeatherParser.parse(data, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        } else null
        ui = if (parsed != null) WeatherUi.Ready(parsed)
        else WeatherUi.Failed(
            result.optString("message").ifBlank { "Weather service returned no usable data. Check your connection and try again." }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.backgroundBrush)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Weather", color = t.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable { refreshKey++ },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Refresh, contentDescription = "Refresh weather", tint = t.accent) }
        }

        // City search (always available; works without location permission)
        OutlinedTextField(
            value = cityInput,
            onValueChange = { cityInput = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search city", fontSize = 14.sp) },
            shape = RoundedCornerShape(26.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (cityInput.isNotBlank()) cityQuery = cityInput.trim() }),
            colors = com.soltini.app.ui.components.glassTextFieldColors()
        )

        when (val s = ui) {
            WeatherUi.Loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = t.accent)
            }
            is WeatherUi.Failed -> GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Weather unavailable", color = t.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(s.message, color = t.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!permissionGranted) Pill("Allow location") { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                    Pill("Try again") { refreshKey++ }
                }
            }
            is WeatherUi.Ready -> WeatherContent(s.data)
        }
    }
}

@Composable
private fun WeatherContent(w: WeatherData) {
    val t = LocalMyraTokens.current
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Text("${w.desc} \u2022 ${w.place}", color = t.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${w.tempC}\u00B0C", color = t.textPrimary, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text("Feels like ${w.feelsC}\u00B0C", color = t.textSecondary, fontSize = 13.sp)
            }
            Icon(iconFor(w.desc), contentDescription = w.desc, tint = t.accent, modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("H ${w.highC}\u00B0  L ${w.lowC}\u00B0", color = t.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${w.humidity}% humidity", color = t.textSecondary, fontSize = 12.sp)
            }
        }
    }

    if (w.hourly.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            w.hourly.forEach { h ->
                GlassCard(modifier = Modifier.weight(1f), cornerRadius = 18.dp, contentPadding = 8.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(h.label, color = t.textSecondary, fontSize = 11.sp, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        Icon(iconFor(h.desc), contentDescription = h.desc, tint = t.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("${h.tempC}\u00B0", color = t.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Wind", "${w.windKmph} km/h", Modifier.weight(1f))
        StatTile("UV index", "${w.uvIndex}", Modifier.weight(1f))
    }

    if (w.days.isNotEmpty()) {
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
            Text("Forecast", color = t.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            w.days.forEach { d ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(d.label, color = t.textPrimary, fontSize = 14.sp, modifier = Modifier.width(52.dp))
                    Icon(iconFor(d.desc), contentDescription = d.desc, tint = t.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(d.desc, color = t.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${d.highC}\u00B0 / ${d.lowC}\u00B0", color = t.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    Text("Source: wttr.in", color = t.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    val t = LocalMyraTokens.current
    GlassCard(modifier = modifier, cornerRadius = 18.dp, contentPadding = 12.dp) {
        Text(label, color = t.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = t.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    val t = LocalMyraTokens.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(t.accentHorizontalGradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) { Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

private fun iconFor(desc: String): ImageVector {
    val d = desc.lowercase()
    return when {
        "thunder" in d -> Icons.Default.Thunderstorm
        "snow" in d || "sleet" in d || "ice" in d -> Icons.Default.AcUnit
        "rain" in d || "drizzle" in d || "shower" in d -> Icons.Default.WaterDrop
        "sun" in d || "clear" in d -> Icons.Default.WbSunny
        else -> Icons.Default.Cloud
    }
}

@SuppressLint("MissingPermission")
private suspend fun lastKnownLatLon(context: Context): Pair<Double, Double>? =
    suspendCancellableCoroutine { cont ->
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc?.let { it.latitude to it.longitude }) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } catch (_: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }
