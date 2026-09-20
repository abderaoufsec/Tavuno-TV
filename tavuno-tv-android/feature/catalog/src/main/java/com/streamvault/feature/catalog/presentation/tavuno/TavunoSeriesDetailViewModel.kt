package com.streamvault.feature.catalog.presentation.tavuno

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.SeriesDetails
import com.streamvault.domain.repository.UnifiedCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TavunoSeriesDetailViewModel @Inject constructor(
    private val catalogRepository: UnifiedCatalogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TavunoSeriesDetailUiState>(TavunoSeriesDetailUiState.Loading)
    val uiState: StateFlow<TavunoSeriesDetailUiState> = _uiState.asStateFlow()

    fun loadSeriesDetails(seriesId: Long) {
        viewModelScope.launch {
            _uiState.value = TavunoSeriesDetailUiState.Loading
            val result = catalogRepository.getSeriesDetails(seriesId)
            if (result is Result.Success) {
                _uiState.value = TavunoSeriesDetailUiState.Success(result.data)
            } else if (result is Result.Error) {
                _uiState.value = TavunoSeriesDetailUiState.Error(
                    result.message ?: "Failed to load series details"
                )
            }
        }
    }
}

sealed class TavunoSeriesDetailUiState {
    object Loading : TavunoSeriesDetailUiState()
    data class Success(val details: SeriesDetails?) : TavunoSeriesDetailUiState()
    data class Error(val message: String) : TavunoSeriesDetailUiState()
}
