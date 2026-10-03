package dev.vitngan.harness.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Checkpoint storage.
 *
 * Reads are scoped to a task so a restored checkpoint can never silently pull
 * another task's state.
 */
@Dao
interface CheckpointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(checkpoint: CheckpointEntity)

    @Query("SELECT * FROM checkpoints WHERE id = :id LIMIT 1")
    fun byId(id: String): CheckpointEntity?

    @Query("SELECT * FROM checkpoints WHERE task_id = :taskId ORDER BY created_at ASC")
    fun forTask(taskId: String): List<CheckpointEntity>

    @Query("SELECT * FROM checkpoints WHERE task_id = :taskId ORDER BY created_at DESC LIMIT 1")
    fun latestForTask(taskId: String): CheckpointEntity?

    @Query("SELECT COUNT(*) FROM checkpoints WHERE task_id = :taskId")
    fun countForTask(taskId: String): Int

    @Query("DELETE FROM checkpoints WHERE id = :id")
    fun deleteById(id: String): Int

    @Query("DELETE FROM checkpoints WHERE task_id = :taskId")
    fun deleteForTask(taskId: String): Int

    @Query("DELETE FROM checkpoints")
    fun deleteAll(): Int
}