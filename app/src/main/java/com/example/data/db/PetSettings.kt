package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pet_settings")
data class PetSettings(
    @PrimaryKey
    val id: Int = 1,
    val selectedPetId: String = "mochi",
    val isOverlayActive: Boolean = false,
    val sizeScale: Float = 1.0f,
    val speedScale: Float = 1.0f,
    val bubbleFrequencyMinutes: Int = 5, // 1, 5, 15, 30, 0 for off
    val isBatterySaverEnabled: Boolean = true,
    val shortcutApp1: String? = null,
    val shortcutApp2: String? = null,
    val shortcutApp3: String? = null,
    val isGameModeEnabled: Boolean = true
)
