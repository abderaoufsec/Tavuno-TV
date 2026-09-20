package com.streamvault.feature.catalog.presentation.tavuno

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.AuthRepository
import com.streamvault.domain.repository.UnifiedCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TavunoHomeViewModel @Inject constructor(
    private val unifiedCatalogRepository: UnifiedCatalogRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TavunoHomeUiState>(TavunoHomeUiState.Loading)
    val uiState: StateFlow<TavunoHomeUiState> = _uiState.asStateFlow()

    init {
        if (authRepository.isLoggedIn()) {
            loadCatalog()
        } else {
            _uiState.value = TavunoHomeUiState.Error("Not authenticated")
        }
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _uiState.value = TavunoHomeUiState.Loading

            val categoriesResult = unifiedCatalogRepository.getCategories()
            val channelsResult = unifiedCatalogRepository.getChannels()
            val moviesResult = unifiedCatalogRepository.getMovies()
            val seriesResult = unifiedCatalogRepository.getSeries()

            when {
                categoriesResult is com.streamvault.domain.model.Result.Success &&
                channelsResult is com.streamvault.domain.model.Result.Success &&
                moviesResult is com.streamvault.domain.model.Result.Success &&
                seriesResult is com.streamvault.domain.model.Result.Success -> {
                    _uiState.value = TavunoHomeUiState.Success(
                        categories = categoriesResult.data,
                        featuredChannels = channelsResult.data.take(12),
                        movies = moviesResult.data.take(12),
                        series = seriesResult.data.take(12)
                    )
                }
                else -> {
                    val errors = listOfNotNull(
                        (categoriesResult as? com.streamvault.domain.model.Result.Error)?.message,
                        (channelsResult as? com.streamvault.domain.model.Result.Error)?.message,
                        (moviesResult as? com.streamvault.domain.model.Result.Error)?.message,
                        (seriesResult as? com.streamvault.domain.model.Result.Error)?.message
                    )
                    _uiState.value = TavunoHomeUiState.Error(
                        message = errors.firstOrNull() ?: "Failed to load catalog"
                    )
                }
            }
        }
    }

    fun selectCategory(categoryId: Long) {
        // Navigation would be handled here
    }

    fun selectChannel(channelId: Long) {
        // Navigation to playback would be handled here
    }
}

sealed interface TavunoHomeUiState {
    data object Loading : TavunoHomeUiState
    data class Success(
        val categories: List<Category>,
        val featuredChannels: List<Channel>,
        val movies: List<Movie>,
        val series: List<Series>
    ) : TavunoHomeUiState
    data class Error(val message: String) : TavunoHomeUiState
}
