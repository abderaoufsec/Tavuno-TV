package com.streamvault.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.data.preferences.TokenStore
import com.streamvault.data.remote.tavuno.TavunoApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val tavunoApiService: TavunoApiService,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<DeviceListUiState>(DeviceListUiState.Loading)
    val uiState: StateFlow<DeviceListUiState> = _uiState.asStateFlow()

    fun loadDevices() {
        viewModelScope.launch {
            _uiState.value = DeviceListUiState.Loading
            try {
                val response = tavunoApiService.listDevices()
                if (response.isSuccessful && response.body() != null) {
                    val currentFingerprint = tokenStore.getDeviceFingerprint()
                    val devices = response.body()!!.map { apiDevice ->
                        DeviceUiModel(
                            id = apiDevice.id,
                            displayName = apiDevice.displayName,
                            platform = apiDevice.platform,
                            lastSeenAt = apiDevice.lastSeenAt ?: "Unknown",
                            isCurrent = apiDevice.isCurrent,
                            isRevoked = apiDevice.isRevoked
                        )
                    }
                    _uiState.value = DeviceListUiState.Loaded(devices)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    _uiState.value = DeviceListUiState.Error(errorBody)
                }
            } catch (e: Exception) {
                _uiState.value = DeviceListUiState.Error(e.message ?: "Failed to load devices")
            }
        }
    }

    fun revokeDevice(deviceId: Int) {
        viewModelScope.launch {
            try {
                val response = tavunoApiService.revokeDevice(deviceId)
                if (response.isSuccessful) {
                    // Reload the list after successful revocation
                    loadDevices()
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    _uiState.value = DeviceListUiState.Error(errorBody)
                }
            } catch (e: Exception) {
                _uiState.value = DeviceListUiState.Error(e.message ?: "Failed to revoke device")
            }
        }
    }
}
