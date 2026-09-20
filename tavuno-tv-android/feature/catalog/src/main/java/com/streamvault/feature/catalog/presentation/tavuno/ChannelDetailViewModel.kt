package com.streamvault.feature.catalog.presentation.tavuno

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.ChannelDetails
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.UnifiedCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelDetailViewModel @Inject constructor(
    private val catalogRepository: UnifiedCatalogRepository,
    private val tavunoCatalogRepository: com.streamvault.data.remote.tavuno.TavunoCatalogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChannelDetailUiState>(ChannelDetailUiState.Loading)
    val uiState: StateFlow<ChannelDetailUiState> = _uiState.asStateFlow()

    fun loadChannelDetails(channelId: Long) {
        viewModelScope.launch {
            _uiState.value = ChannelDetailUiState.Loading
            val result = catalogRepository.getChannelDetails(channelId)
            if (result is Result.Success) {
                _uiState.value = ChannelDetailUiState.Success(result.data)
            } else if (result is Result.Error) {
                _uiState.value = ChannelDetailUiState.Error(
                    result.message ?: "Failed to load channel details"
                )
            }
        }
    }

    suspend fun authorizePlayback(channelId: Long): Result<com.streamvault.data.remote.tavuno.PlaybackResponse> {
        return tavunoCatalogRepository.authorizeLivePlayback(channelId.toInt())
    }
}

sealed class ChannelDetailUiState {
    object Loading : ChannelDetailUiState()
    data class Success(val details: ChannelDetails?) : ChannelDetailUiState()
    data class Error(val message: String) : ChannelDetailUiState()
}
