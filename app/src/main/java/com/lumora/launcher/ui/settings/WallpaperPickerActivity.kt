package com.lumora.launcher.ui.settings

import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumora.launcher.R
import com.lumora.launcher.ui.theme.LumoraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Simple wallpaper picker using the system Photo Picker / GET_CONTENT.
 * Sets the system wallpaper (home screen).
 */
class WallpaperPickerActivity : ComponentActivity() {

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            setWallpaper(uri)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LumoraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.choose_wallpaper), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { pickImage.launch("image/*") }) {
                                Text(stringResource(R.string.choose_wallpaper))
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { finish() }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setWallpaper(uri: Uri) {
        val scope = kotlinx.coroutines.MainScope()
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        WallpaperManager.getInstance(this@WallpaperPickerActivity)
                            .setStream(stream)
                    }
                }
            } catch (_: Exception) {
                // Ignore – user can try again
            } finally {
                finish()
            }
        }
    }
}
