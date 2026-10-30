package com.smartattendance.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.smartattendance.app.data.local.AttendanceDatabase
import com.smartattendance.app.data.remote.SupabaseApi
import com.smartattendance.app.data.remote.TokenVault

class SmartAttendanceApp: Application() {
    val database by lazy {
        Room.databaseBuilder(this, AttendanceDatabase::class.java, "smart-attendance-demo.db")
            .addMigrations(AttendanceDatabase.MIGRATION_1_2)
            .build()
    }
    val api by lazy { SupabaseApi(TokenVault(this)) }
    val preferences by lazy { getSharedPreferences("settings",Context.MODE_PRIVATE) }
}
