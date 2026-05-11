package com.oblutack.timenote.data.database

import androidx.room.RoomDatabaseConstructor

actual object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    actual override fun initialize(): AppDatabase = AppDatabase_Impl() // <-- ADD 'actual' HERE
}