package com.drafty.feature.notebooks.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drafty.core.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the settings screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.penThickness,
        preferencesRepository.defaultTemplate,
        preferencesRepository.palmRejectionEnabled,
        preferencesRepository.themeMode,
    ) { thickness, template, palmRejection, theme ->
        SettingsUiState(
            penThickness = thickness,
            defaultTemplate = template,
            palmRejectionEnabled = palmRejection,
            themeMode = theme,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setPenThickness(thickness: Float) {
        viewModelScope.launch { preferencesRepository.setPenThickness(thickness) }
    }

    fun setDefaultTemplate(template: String) {
        viewModelScope.launch { preferencesRepository.setDefaultTemplate(template) }
    }

    fun setPalmRejectionEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setPalmRejectionEnabled(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }
}

data class SettingsUiState(
    val penThickness: Float = 4f,
    val defaultTemplate: String = "BLANK",
    val palmRejectionEnabled: Boolean = true,
    val themeMode: String = "system",
)
