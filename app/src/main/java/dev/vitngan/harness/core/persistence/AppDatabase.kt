package dev.vitngan.harness.core.persistence

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The Room schema.
 *
 * One entity, `checkpoints`. Schema changes are additive-only from here; a
 * version bump follows a migration rather than a destructive fallback, so a
 * restore never silently drops data.
 */
@Database(
    entities = [CheckpointEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    /** Checkpoint storage. */
    abstract fun checkpointDao(): CheckpointDao

    companion object {
        const val NAME = "vit-ngan-harness.db"
    }
}