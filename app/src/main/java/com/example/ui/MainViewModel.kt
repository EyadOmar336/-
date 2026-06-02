package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.CustomReminder
import com.example.data.db.PetSettings
import com.example.data.repository.PetRepository
import com.example.service.FloatingPetService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val repository: PetRepository) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.initializeDatabaseIfEmpty()
        }
    }

    val settings: StateFlow<PetSettings> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PetSettings()
        )

    val reminders: StateFlow<List<CustomReminder>> = repository.reminders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectPet(petId: String) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(selectedPetId = petId) }
        }
    }

    fun updateSize(scale: Float) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(sizeScale = scale) }
        }
    }

    fun updateSpeed(scale: Float) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(speedScale = scale) }
        }
    }

    fun updateFrequency(minutes: Int) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(bubbleFrequencyMinutes = minutes) }
        }
    }

    fun toggleBatterySaver(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(isBatterySaverEnabled = enabled) }
        }
    }

    fun updateShortcutApp(index: Int, packageName: String?) {
        viewModelScope.launch {
            repository.updateSettings {
                when (index) {
                    1 -> it.copy(shortcutApp1 = packageName)
                    2 -> it.copy(shortcutApp2 = packageName)
                    3 -> it.copy(shortcutApp3 = packageName)
                    else -> it
                }
            }
        }
    }

    fun toggleGameMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(isGameModeEnabled = enabled) }
        }
    }

    fun addReminder(text: String) {
        viewModelScope.launch {
            repository.addReminder(text)
        }
    }

    fun removeReminder(reminder: CustomReminder) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
        }
    }

    fun toggleOverlay(context: Context, activate: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(isOverlayActive = activate) }
            val intent = Intent(context, FloatingPetService::class.java)
            if (activate) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}

class MainViewModelFactory(private val repository: PetRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
