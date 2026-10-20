package app.treelune.core.scheduling

/**
 * Whether the app is writing a whole set of things through its services, the demo installed or
 * removed, a part of a backup imported: the schedulers wait for it to end (CoreScheduler), as one
 * acting on a set half written would act on what the set itself is about to write — a goal's
 * attempt opened before the goal's own attempts arrive.
 */
object SchedulersHeld {

    @Volatile
    var held: Boolean = false
        private set

    /** [block] run with the schedulers held off. */
    suspend fun <T> during(block: suspend () -> T): T {
        held = true
        try {
            return block()
        } finally {
            held = false
        }
    }
}
