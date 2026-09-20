package com.streamvault.feature.catalog.presentation.tavuno

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.Competition
import com.streamvault.domain.model.Match
import com.streamvault.domain.model.MatchDetails
import com.streamvault.domain.model.Result
import com.streamvault.data.remote.tavuno.TavunoSportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Tavuno sports screens (M11).
 */
@HiltViewModel
class TavunoSportsViewModel @Inject constructor(
    private val sportsRepository: TavunoSportsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SportsUiState>(SportsUiState.Loading)
    val uiState: StateFlow<SportsUiState> = _uiState.asStateFlow()

    init {
        loadSports()
    }

    fun loadSports() {
        viewModelScope.launch {
            _uiState.value = SportsUiState.Loading

            val competitionsResult = sportsRepository.getCompetitions()
            val liveMatchesResult = sportsRepository.getMatches(status = "live", limit = 10)
            val upcomingMatchesResult = sportsRepository.getMatches(status = "upcoming", limit = 20)

            when {
                competitionsResult is Result.Success &&
                liveMatchesResult is Result.Success &&
                upcomingMatchesResult is Result.Success -> {
                    _uiState.value = SportsUiState.Success(
                        competitions = competitionsResult.data,
                        liveMatches = liveMatchesResult.data,
                        upcomingMatches = upcomingMatchesResult.data
                    )
                }
                else -> {
                    val errors = listOfNotNull(
                        (competitionsResult as? Result.Error)?.message,
                        (liveMatchesResult as? Result.Error)?.message,
                        (upcomingMatchesResult as? Result.Error)?.message
                    )
                    _uiState.value = SportsUiState.Error(
                        message = errors.firstOrNull() ?: "Failed to load sports"
                    )
                }
            }
        }
    }

    fun loadCompetitionMatches(competitionId: Int) {
        viewModelScope.launch {
            _uiState.value = SportsUiState.Loading

            val matchesResult = sportsRepository.getCompetitionMatches(competitionId)

            when (matchesResult) {
                is Result.Success -> {
                    _uiState.value = SportsUiState.CompetitionMatches(
                        matches = matchesResult.data
                    )
                }
                is Result.Error -> {
                    _uiState.value = SportsUiState.Error(
                        message = matchesResult.message ?: "Failed to load matches"
                    )
                }
                is Result.Loading -> {
                    // Already set to Loading above
                }
            }
        }
    }

    fun loadMatchDetails(matchId: Int) {
        viewModelScope.launch {
            _uiState.value = SportsUiState.Loading

            val matchDetailsResult = sportsRepository.getMatchDetails(matchId)

            when (matchDetailsResult) {
                is Result.Success -> {
                    _uiState.value = SportsUiState.MatchDetailsLoaded(
                        matchDetails = matchDetailsResult.data
                    )
                }
                is Result.Error -> {
                    _uiState.value = SportsUiState.Error(
                        message = matchDetailsResult.message ?: "Failed to load match details"
                    )
                }
                is Result.Loading -> {
                    // Already set to Loading above
                }
            }
        }
    }
}

sealed interface SportsUiState {
    object Loading : SportsUiState
    data class Success(
        val competitions: List<Competition>,
        val liveMatches: List<Match>,
        val upcomingMatches: List<Match>
    ) : SportsUiState
    data class CompetitionMatches(val matches: List<Match>) : SportsUiState
    data class MatchDetailsLoaded(val matchDetails: MatchDetails) : SportsUiState
    data class Error(val message: String) : SportsUiState
}
