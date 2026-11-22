package com.fleetmc.verity.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fleetmc.verity.data.Addon
import com.fleetmc.verity.ui.HomeUiState
import com.fleetmc.verity.ui.LoadState
import com.fleetmc.verity.ui.components.AddonCard
import com.fleetmc.verity.ui.components.ScreenHeader
import com.fleetmc.verity.ui.components.StateMessage

@Composable
fun HomeScreen(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onOpen: (Addon) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            ScreenHeader(title = "Verity Mod", subtitle = "Add-ons for Minecraft Bedrock")
        }
        item(key = "search") {
            SearchField(query = state.query, onQueryChange = onQueryChange)
        }
        if (state.categories.isNotEmpty()) {
            item(key = "categories") {
                CategoryChips(
                    categories = state.categories,
                    selected = state.selectedCategory,
                    onSelect = onCategorySelect,
                )
            }
        }
        when (state.loadState) {
            LoadState.Loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            LoadState.Failed -> item(key = "failed") {
                StateMessage("Catalogue unavailable", "The add-on list could not be read from the app.")
            }
            is LoadState.Loaded -> {
                if (state.addons.isEmpty()) {
                    item(key = "empty") {
                        StateMessage("No add-ons found", "Try another search or pick a different category.")
                    }
                } else {
                    items(state.addons, key = { it.id }) { addon ->
                        AddonCard(addon = addon, onClick = { onOpen(addon) })
                    }
                }
            }
        }
    }
}
