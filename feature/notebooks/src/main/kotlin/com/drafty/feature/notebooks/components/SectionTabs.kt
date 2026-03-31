package com.drafty.feature.notebooks.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drafty.core.domain.model.Section

/**
 * Scrollable tab row for switching between sections within a notebook.
 * Includes a trailing "+" button to add new sections.
 * Long-press on a section tab opens a context menu for rename/delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SectionTabs(
    sections: List<Section>,
    selectedSectionId: String?,
    onSectionSelected: (String) -> Unit = {},
    onAddSection: () -> Unit = {},
    onRenameSection: (Section) -> Unit = {},
    onDeleteSection: (Section) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val selectedIndex = sections.indexOfFirst { it.id == selectedSectionId }.coerceAtLeast(0)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (sections.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                modifier = Modifier.weight(1f),
                edgePadding = 12.dp,
            ) {
                sections.forEachIndexed { index, section ->
                    var showMenu by remember { mutableStateOf(false) }

                    Tab(
                        selected = index == selectedIndex,
                        onClick = { onSectionSelected(section.id) },
                        modifier = Modifier.combinedClickable(
                            onClick = { onSectionSelected(section.id) },
                            onLongClick = { showMenu = true },
                        ),
                    ) {
                        Text(
                            text = section.title,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    showMenu = false
                                    onRenameSection(section)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showMenu = false
                                    onDeleteSection(section)
                                },
                            )
                        }
                    }
                }
            }
        }

        IconButton(onClick = onAddSection) {
            Icon(Icons.Default.Add, contentDescription = "Add section")
        }
    }
}
