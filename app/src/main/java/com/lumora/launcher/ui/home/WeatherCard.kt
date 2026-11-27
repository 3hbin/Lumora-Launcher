package com.lumora.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumora.launcher.data.WeatherInfo

@Composable
fun WeatherCard(
    weather: WeatherInfo?,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.28f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(Modifier.width(10.dp))
                Text("…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            } else if (weather != null) {
                Icon(
                    imageVector = weatherIcon(weather.iconName),
                    contentDescription = weather.description,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${weather.temperature.toInt()}°",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp)
                    )
                    Text(
                        text = "${weather.cityName} · ${weather.description}",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            } else {
                Icon(Icons.Outlined.CloudQueue, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.width(8.dp))
                Text("Weather unavailable", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun weatherIcon(name: String): ImageVector = when (name) {
    "clear" -> Icons.Outlined.WbSunny
    "partly_cloudy" -> Icons.Outlined.Cloud
    "rain", "drizzle" -> Icons.Outlined.Umbrella
    "snow" -> Icons.Outlined.AcUnit
    "thunder" -> Icons.Outlined.Thunderstorm
    else -> Icons.Outlined.CloudQueue
}
