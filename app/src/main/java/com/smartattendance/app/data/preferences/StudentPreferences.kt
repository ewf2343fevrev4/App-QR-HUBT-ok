package com.smartattendance.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.studentPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "student_preferences")

class StudentPreferences(private val context: Context) {
    companion object {
        val PAGE_SIZE_KEY = intPreferencesKey("student_page_size")
        const val DEFAULT_PAGE_SIZE = 10
        val ALLOWED_PAGE_SIZES = listOf(10, 20, 50, 100)
    }

    val pageSizeFlow: Flow<Int> = context.studentPreferencesDataStore.data.map { preferences ->
        val size = preferences[PAGE_SIZE_KEY] ?: DEFAULT_PAGE_SIZE
        if (size in ALLOWED_PAGE_SIZES) size else DEFAULT_PAGE_SIZE
    }

    suspend fun setPageSize(size: Int) {
        val validSize = if (size in ALLOWED_PAGE_SIZES) size else DEFAULT_PAGE_SIZE
        context.studentPreferencesDataStore.edit { preferences ->
            preferences[PAGE_SIZE_KEY] = validSize
        }
    }
}

