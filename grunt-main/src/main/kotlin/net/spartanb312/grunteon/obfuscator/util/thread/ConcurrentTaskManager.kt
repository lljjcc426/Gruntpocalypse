package net.spartanb312.grunteon.obfuscator.util.thread

import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight concurrent task manager by using coroutine
 * Support Delay task and Repeat task
 */
fun ConcurrentTaskManager(
    name: String,
    threads: Int = Runtime.getRuntime().availableProcessors(),
    lite: Boolean = true
): ConcurrentTaskManager = ConcurrentTaskManager(name, lite, newCoroutineScope(threads, name))

interface TaskManager : CoroutineScope {

    fun runLater(delayTime: Int, block: suspend CoroutineScope.() -> Unit) : Boolean
    fun runRepeat(delayTime: Int, suspended: Boolean = false, block: suspend CoroutineScope.() -> Unit): RepeatUnit
    fun shutdown()
    fun available(): Boolean

    class RepeatUnit(
        private val taskManager: TaskManager,
        private var delayTime: Int,
        suspended: Boolean = false,
        private val block: suspend CoroutineScope.() -> Unit
    ) {
        private val suspended = AtomicBoolean(suspended)
        private val isAlive = AtomicBoolean(true)
        private val isRunning = AtomicBoolean(false)
        private var nextStartTime = 0L

        val isDead get() = !isAlive.get()
        val isSuspended get() = suspended.get()

        fun suspend() = suspended.set(true)

        fun resume() = suspended.set(false)

        fun stop() = isAlive.set(false)

        fun resetDelay(delayTime: Int) {
            this.delayTime = delayTime
        }

        fun invoke() {
            if (System.currentTimeMillis() > nextStartTime) {
                if (!isRunning.getAndSet(true)) {
                    nextStartTime = System.currentTimeMillis() + delayTime
                    taskManager.launch(Dispatchers.Default) {
                        runCatching { block() }.onFailure { it.printStackTrace() }
                        isRunning.set(false)
                    }
                }
            }
        }
    }

}

class ConcurrentTaskManager(
    name: String,
    lite: Boolean = true,
    scope: CoroutineScope,
) : TaskManager, CoroutineScope by scope {

    private val working = AtomicBoolean(true)
    private val scheduledTasks = CopyOnWriteArraySet<Pair<suspend CoroutineScope.() -> Unit, Long>>()//Task, StartTime
    private val repeatUnits = CopyOnWriteArraySet<TaskManager.RepeatUnit>()

    init {
        //launch a daemon thread for those scheduled tasks
        object : Thread("$name-Daemon") {
            override fun run() {
                while (working.get()) {
                    val currentTime = System.currentTimeMillis()
                    scheduledTasks.removeIf { (task, startTime) ->
                        if (currentTime > startTime) {
                            launch(block = task)
                            true
                        } else false
                    }
                    repeatUnits.removeIf {
                        if (it.isDead) true
                        else {
                            if (!it.isSuspended) it.invoke()
                            false
                        }
                    }
                    if (lite) sleep(1)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    //Delay task
    override fun runLater(
        delayTime: Int,
        block: suspend CoroutineScope.() -> Unit
    ): Boolean = scheduledTasks.add(Pair(block, System.currentTimeMillis() + delayTime))

    //Repeat task
    override fun runRepeat(
        delayTime: Int,
        suspended: Boolean,
        block: suspend CoroutineScope.() -> Unit
    ): TaskManager.RepeatUnit = TaskManager.RepeatUnit(this, delayTime, suspended, block).also { repeatUnits.add(it) }

    override fun shutdown() {
        working.set(false)
        this.coroutineContext.cancel()
    }

    override fun available(): Boolean = working.get()

}

private val taskManagerId = AtomicInteger()
val nextTaskManagerId = taskManagerId.getAndIncrement()

inline fun <T> taskManager(
    name: String = "DefaultTaskManager-$nextTaskManagerId",
    crossinline block: suspend ConcurrentTaskManager.() -> T
) = ConcurrentTaskManager(name).apply {
    runBlocking {
        block()
        shutdown()
    }
}
