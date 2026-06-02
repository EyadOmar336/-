package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_reminders")
data class CustomReminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String
)
