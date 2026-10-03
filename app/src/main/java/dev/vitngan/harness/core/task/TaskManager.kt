package dev.vitngan.harness.core.task

/** Creates and advances tasks, rejecting illegal transitions. */
interface TaskManager {

    fun create(title: String, description: String = "", workspaceId: String? = null): Task

    fun get(id: String): Task?

    fun all(): List<Task>

    /** Applies [next] when legal. Returns null when the transition is illegal. */
    fun transition(id: String, next: TaskStatus, reason: String? = null): Task?

    fun cancel(id: String, reason: String? = null): Task?
}