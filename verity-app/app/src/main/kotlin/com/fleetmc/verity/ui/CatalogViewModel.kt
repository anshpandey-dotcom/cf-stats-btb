package com.fleetmc.verity.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleetmc.verity.data.Addon
import com.fleetmc.verity.data.CatalogRepository
import com.fleetmc.verity.data.UnlockStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LoadState {
    data object Loading : LoadState
    data object Failed : LoadState
    data class Loaded(val addons: List<Addon>) : LoadState
}

data class HomeUiState(
    val loadState: LoadState = LoadState.Loading,
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val query: String = "",
    val addons: List<Addon> = emptyList(),
)

class CatalogViewModel(
    private val repository: CatalogRepository,
    unlockStore: UnlockStore,
) : ViewModel() {

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)

    val loadState: StateFlow<LoadState> = _loadState

    val unlocked: StateFlow<Set<String>> = unlockStore.unlockedIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val home: StateFlow<HomeUiState> = combine(_loadState, query, category) { state, text, selected ->
        val all = (state as? LoadState.Loaded)?.addons.orEmpty()
        HomeUiState(
            loadState = state,
            categories = all.map { it.category }.distinct().sorted(),
            selectedCategory = selected,
            query = text,
            addons = all.filter { matches(it, text, selected) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    val saved: StateFlow<List<Addon>> = combine(_loadState, unlocked) { state, ids ->
        (state as? LoadState.Loaded)?.addons.orEmpty().filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        load()
    }

    fun load() {
        _loadState.value = LoadState.Loading
        viewModelScope.launch {
            _loadState.value = try {
                LoadState.Loaded(repository.load())
            } catch (error: Exception) {
                LoadState.Failed
            }
        }
    }

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun onCategorySelect(selected: String?) {
        category.update { current -> if (current == selected) null else selected }
    }

    private fun matches(addon: Addon, text: String, selected: String?): Boolean {
        if (selected != null && addon.category != selected) return false
        val needle = text.trim()
        if (needle.isEmpty()) return true
        return addon.name.contains(needle, ignoreCase = true) ||
            addon.description.contains(needle, ignoreCase = true) ||
            addon.category.contains(needle, ignoreCase = true)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
