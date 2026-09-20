package com.streamvault.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TavunoDataSourceStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "tavuno_data_source",
        Context.MODE_PRIVATE
    )

    enum class DataSourceType {
        TAVUNO,
        PROVIDER
    }

    private val keyDataSource = "data_source"

    private val _dataSource = MutableStateFlow(getInitialDataSource())
    val dataSource: StateFlow<DataSourceType> = _dataSource.asStateFlow()

    private fun getInitialDataSource(): DataSourceType {
        val value = prefs.getString(keyDataSource, DataSourceType.TAVUNO.name)
        return try {
            DataSourceType.valueOf(value ?: DataSourceType.TAVUNO.name)
        } catch (e: IllegalArgumentException) {
            DataSourceType.TAVUNO
        }
    }

    fun getDataSource(): Flow<DataSourceType> = dataSource

    suspend fun setDataSource(source: DataSourceType) {
        prefs.edit {
            putString(keyDataSource, source.name)
        }
        _dataSource.value = source
    }

    suspend fun clear() {
        prefs.edit {
            clear()
        }
        _dataSource.value = DataSourceType.TAVUNO
    }
}

// Extension function for SharedPreferences edit
private suspend fun SharedPreferences.edit(block: SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    block(editor)
    editor.apply()
}
