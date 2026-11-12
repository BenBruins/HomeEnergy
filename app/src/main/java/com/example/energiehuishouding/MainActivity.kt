package com.example.energiehuishouding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.energiehuishouding.data.SyncWorker
import com.example.energiehuishouding.ui.DashboardScreen
import com.example.energiehuishouding.ui.MainViewModel
import com.example.energiehuishouding.ui.theme.EnergieHuishoudingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as SolarApplication
        val viewModel: MainViewModel by viewModels {
            MainViewModel.Factory(app.repository, app.settingsRepository, app.historyImporter)
        }

        SyncWorker.enqueue(applicationContext)

        enableEdgeToEdge()
        setContent {
            EnergieHuishoudingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}
