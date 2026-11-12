package com.example.energiehuishouding

import android.app.Application
import com.example.energiehuishouding.data.HistoryImporter
import com.example.energiehuishouding.data.InverterRepository
import com.example.energiehuishouding.data.SettingsRepository
import com.example.energiehuishouding.data.local.SolarDatabase

class SolarApplication : Application() {
    lateinit var repository: InverterRepository
    lateinit var settingsRepository: SettingsRepository
    lateinit var historyImporter: HistoryImporter

    override fun onCreate() {
        super.onCreate()
        val database = SolarDatabase.getDatabase(this)
        settingsRepository = SettingsRepository(this)
        repository = InverterRepository(database.solarDao(), settingsRepository)
        historyImporter = HistoryImporter(this)
    }
}
