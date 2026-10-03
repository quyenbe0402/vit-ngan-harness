package dev.vitngan.harness.core.persistence

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The Room schema.
 *
 * One entity is registered: checkpoints are the first thing the harness must
 * be able to restore, and their shape is stable enough to persist now. Schema
 * changes are additive-only from here; a version bump follows a migration
 * rather than a destructive fallback, so a restore never silently drops data.
 */
@Database(
    entities = [CheckpointEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        const val NAME = "vit-ngan-harness.db"
    }
}