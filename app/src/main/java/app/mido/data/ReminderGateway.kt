package app.mido.data

/** What the repository needs from the alarm system, so the data layer stays Android-free. */
interface ReminderGateway {
    /** (Re)arms the reminder for [task], or cancels it if it no longer needs one. */
    fun schedule(task: TaskEntity)
    fun cancel(taskId: Long)

    object None : ReminderGateway {
        override fun schedule(task: TaskEntity) = Unit
        override fun cancel(taskId: Long) = Unit
    }
}
