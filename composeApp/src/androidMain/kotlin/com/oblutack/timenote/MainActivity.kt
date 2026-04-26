package com.oblutack.timenote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import com.oblutack.timenote.data.database.AppDatabase
import com.oblutack.timenote.data.database.instantiateDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Create the Android-specific database builder
        val dbBuilder = Room.databaseBuilder(
            context = applicationContext,
            klass = AppDatabase::class.java,
            name = "timenotes.db"
        )

        // 2. Attach the universal SQLite driver using our common function
        val database = instantiateDatabase(dbBuilder)

        setContent {
            // 3. Pass the database down into our common App UI!
            App(database = database)
        }
    }
}