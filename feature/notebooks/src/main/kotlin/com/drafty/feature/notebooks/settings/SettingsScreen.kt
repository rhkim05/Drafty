package com.drafty.feature.notebooks.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Settings screen for app preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Section: Drawing defaults
            Text(
                text = "Drawing defaults",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text("Pen thickness") },
                supportingContent = {
                    Slider(
                        value = uiState.penThickness,
                        onValueChange = viewModel::setPenThickness,
                        valueRange = 1f..20f,
                        steps = 18,
                    )
                },
            )

            // Default template
            var templateExpanded by remember { mutableStateOf(false) }
            val templates = listOf("BLANK", "LINED", "GRID", "DOTTED", "CORNELL")

            ListItem(
                headlineContent = { Text("Default paper template") },
                supportingContent = {
                    ExposedDropdownMenuBox(
                        expanded = templateExpanded,
                        onExpandedChange = { templateExpanded = it },
                    ) {
                        TextField(
                            value = uiState.defaultTemplate,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = templateExpanded,
                            onDismissRequest = { templateExpanded = false },
                        ) {
                            templates.forEach { template ->
                                DropdownMenuItem(
                                    text = { Text(template) },
                                    onClick = {
                                        viewModel.setDefaultTemplate(template)
                                        templateExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Section: Input
            Text(
                text = "Input",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text("Palm rejection") },
                supportingContent = { Text("Reject palm touches when using stylus") },
                trailingContent = {
                    Switch(
                        checked = uiState.palmRejectionEnabled,
                        onCheckedChange = viewModel::setPalmRejectionEnabled,
                    )
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Section: Appearance
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            var themeExpanded by remember { mutableStateOf(false) }
            val themeOptions = listOf("system" to "System", "light" to "Light", "dark" to "Dark")

            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    ExposedDropdownMenuBox(
                        expanded = themeExpanded,
                        onExpandedChange = { themeExpanded = it },
                    ) {
                        val displayName = themeOptions.find { it.first == uiState.themeMode }?.second ?: "System"
                        TextField(
                            value = displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false },
                        ) {
                            themeOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setThemeMode(value)
                                        themeExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Section: About
            Text(
                text = "About",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text("Drafty") },
                supportingContent = { Text("Version 0.1.0") },
            )
        }
    }
}
