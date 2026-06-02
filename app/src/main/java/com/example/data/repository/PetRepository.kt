package com.example.data.repository

import com.example.data.db.CustomReminder
import com.example.data.db.PetDao
import com.example.data.db.PetSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PetRepository(private val petDao: PetDao) {

    val settings: Flow<PetSettings> = petDao.getSettingsFlow().map {
        it ?: PetSettings()
    }

    val reminders: Flow<List<CustomReminder>> = petDao.getAllRemindersFlow()

    suspend fun initializeDatabaseIfEmpty() {
        val currentSettings = petDao.getSettingsDirect()
        if (currentSettings == null) {
            petDao.saveSettings(PetSettings())
        }

        val currentReminders = petDao.getAllRemindersDirect()
        if (currentReminders.isEmpty()) {
            val defaults = listOf(
                CustomReminder(text = "لا تنسَ شرب كوب ماء الآن! 💧"),
                CustomReminder(text = "استرخِ، تَنفّس بعمق لخمس ثوانٍ... 🧘‍♂️"),
                CustomReminder(text = "مضت ساعة، حرّك عينيك ورقبتك قليلاً! 👀"),
                CustomReminder(text = "أنا أراقبك! ركّز في عملك أو دراستك 🎯"),
                CustomReminder(text = "ابتسم! يومك جميل ومثمر إن شاء الله 🌟")
            )
            petDao.seedReminders(defaults)
        }
    }

    suspend fun updateSettings(updater: (PetSettings) -> PetSettings) {
        val current = petDao.getSettingsDirect() ?: PetSettings()
        val updated = updater(current)
        petDao.saveSettings(updated)
    }

    suspend fun addReminder(text: String) {
        if (text.isNotBlank()) {
            petDao.insertReminder(CustomReminder(text = text.trim()))
        }
    }

    suspend fun deleteReminder(reminder: CustomReminder) {
        petDao.deleteReminder(reminder)
    }
}
