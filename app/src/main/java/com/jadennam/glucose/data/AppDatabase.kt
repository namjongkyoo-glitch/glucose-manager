package com.jadennam.glucose.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class, SettingsEntity::class, ReadingEntity::class,
        MealEntity::class, ExerciseEntity::class, WeightEntity::class, MedicationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun settingsDao(): SettingsDao
    abstract fun recordDao(): RecordDao

    companion object {
        // Add Migration objects here when the schema version increases. Never use destructive fallback.
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "glucose.db").build()
    }
}
