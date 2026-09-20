package com.streamvault.feature.catalog.presentation.tavuno

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.MovieDetails
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.UnifiedCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TavunoMovieDetailViewModel @Inject constructor(
    private val catalogRepository: UnifiedCatalogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TavunoMovieDetailUiState>(TavunoMovieDetailUiState.Loading)
    val uiState: StateFlow<TavunoMovieDetailUiState> = _uiState.asStateFlow()

    fun loadMovieDetails(movieId: Long) {
        viewModelScope.launch {
            _uiState.value = TavunoMovieDetailUiState.Loading
            val result = catalogRepository.getMovieDetails(movieId)
            if (result is Result.Success) {
                _uiState.value = TavunoMovieDetailUiState.Success(result.data)
            } else if (result is Result.Error) {
                _uiState.value = TavunoMovieDetailUiState.Error(
                    result.message ?: "Failed to load movie details"
                )
            }
        }
    }
}

sealed class TavunoMovieDetailUiState {
    object Loading : TavunoMovieDetailUiState()
    data class Success(val details: MovieDetails?) : TavunoMovieDetailUiState()
    data class Error(val message: String) : TavunoMovieDetailUiState()
}
