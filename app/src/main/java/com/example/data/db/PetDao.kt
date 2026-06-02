package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {
    @Query("SELECT * FROM pet_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<PetSettings?>

    @Query("SELECT * FROM pet_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): PetSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: PetSettings)

    @Query("SELECT * FROM custom_reminders ORDER BY id DESC")
    fun getAllRemindersFlow(): Flow<List<CustomReminder>>

    @Query("SELECT * FROM custom_reminders")
    suspend fun getAllRemindersDirect(): List<CustomReminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: CustomReminder)

    @Delete
    suspend fun deleteReminder(reminder: CustomReminder)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedReminders(reminders: List<CustomReminder>)
}
