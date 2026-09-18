package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UnlockedWallpapersDataStore
import com.example.data.model.Wallpaper
import com.example.data.repository.WallpaperRepository
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val selectedCategory: String = "All",
    val wallpapers: List<Wallpaper> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null
)

data class SearchUiState(
    val query: String = "",
    val results: List<Wallpaper> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null
)

class WallpapersViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WallpaperRepository(application)
    private val unlockedDataStore = UnlockedWallpapersDataStore(application)

    val categories: List<String> = listOf(
        "All", "Gaming", "GTA 6", "Anime", "Nature",
        "Abstract", "Cars", "Sports", "Space", "Dark",
        "Minimal", "4K", "Other"
    )

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    // Unlocked wallpaper IDs flow
    val unlockedWallpaperIds: StateFlow<Set<String>> = unlockedDataStore.unlockedWallpaperIds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var searchDebounceJob: Job? = null

    init {
        loadWallpapers(initial = true)
    }

    fun selectCategory(category: String) {
        if (_homeState.value.selectedCategory == category) return
        _homeState.update { it.copy(selectedCategory = category) }
        loadWallpapers(initial = true)
    }

    fun refreshWallpapers() {
        _homeState.update { it.copy(isRefreshing = true) }
        loadWallpapers(isRefresh = true)
    }

    fun loadWallpapers(initial: Boolean = false, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (initial) {
                _homeState.update { it.copy(isLoading = true, error = null) }
                lastDocumentSnapshot = null
            } else if (isRefresh) {
                lastDocumentSnapshot = null
            }

            val category = _homeState.value.selectedCategory
            val result = repository.getWallpapers(
                category = category,
                lastSnapshot = null,
                pageSize = 20
            )

            result.fold(
                onSuccess = { (newWallpapers, nextSnapshot) ->
                    lastDocumentSnapshot = nextSnapshot
                    _homeState.update {
                        it.copy(
                            wallpapers = newWallpapers,
                            isLoading = false,
                            isRefreshing = false,
                            canLoadMore = newWallpapers.size >= 20 && nextSnapshot != null,
                            error = null
                        )
                    }
                },
                onFailure = { err ->
                    _homeState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = err.message ?: "Failed to load wallpapers"
                        )
                    }
                }
            )
        }
    }

    fun loadMoreWallpapers() {
        val state = _homeState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return

        viewModelScope.launch {
            _homeState.update { it.copy(isLoadingMore = true) }

            val result = repository.getWallpapers(
                category = state.selectedCategory,
                lastSnapshot = lastDocumentSnapshot,
                pageSize = 20
            )

            result.fold(
                onSuccess = { (moreWallpapers, nextSnapshot) ->
                    lastDocumentSnapshot = nextSnapshot
                    _homeState.update { current ->
                        val combined = (current.wallpapers + moreWallpapers).distinctBy { it.id }
                        current.copy(
                            wallpapers = combined,
                            isLoadingMore = false,
                            canLoadMore = moreWallpapers.size >= 20 && nextSnapshot != null
                        )
                    }
                },
                onFailure = {
                    _homeState.update { it.copy(isLoadingMore = false) }
                }
            )
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchState.update { it.copy(query = newQuery) }

        searchDebounceJob?.cancel()
        if (newQuery.isBlank()) {
            _searchState.update { it.copy(results = emptyList(), hasSearched = false, isSearching = false) }
            return
        }

        searchDebounceJob = viewModelScope.launch {
            delay(350) // Debounce user keystrokes
            executeSearch(newQuery)
        }
    }

    fun executeSearch(query: String = _searchState.value.query) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _searchState.update { it.copy(isSearching = true, error = null) }
            val result = repository.searchWallpapers(query)
            result.fold(
                onSuccess = { results ->
                    _searchState.update {
                        it.copy(
                            results = results,
                            isSearching = false,
                            hasSearched = true,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _searchState.update {
                        it.copy(
                            isSearching = false,
                            hasSearched = true,
                            error = error.message ?: "Search failed"
                        )
                    }
                }
            )
        }
    }

    fun clearSearch() {
        searchDebounceJob?.cancel()
        _searchState.update { SearchUiState() }
    }

    fun unlockWallpaper(wallpaperId: String) {
        viewModelScope.launch {
            unlockedDataStore.unlockWallpaper(wallpaperId)
        }
    }

    fun recordDownloadOrSet(wallpaperId: String) {
        viewModelScope.launch {
            repository.incrementDownloads(wallpaperId)
            // Increment in local list state for immediate UI responsiveness
            _homeState.update { current ->
                current.copy(
                    wallpapers = current.wallpapers.map { wp ->
                        if (wp.id == wallpaperId) wp.copy(downloads = wp.downloads + 1) else wp
                    }
                )
            }
            _searchState.update { current ->
                current.copy(
                    results = current.results.map { wp ->
                        if (wp.id == wallpaperId) wp.copy(downloads = wp.downloads + 1) else wp
                    }
                )
            }
        }
    }
}
