package com.soltini.app.agent

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskQueueStoreTest {

    @Test
    fun saveAndLoadKeepStatusAndProgress() {
        val store = TaskQueueStore(RuntimeEnvironment.getApplication())
        val q = TaskQueue(
            "t1", "a phir b",
            listOf(
                QueueItem("a", QueueItemStatus.DONE),
                QueueItem("b", QueueItemStatus.RUNNING, 1, "", "opened app | typed text")
            )
        )
        store.save(q)

        val loaded = store.load()!!
        assertEquals(2, loaded.items.size)
        assertEquals(QueueItemStatus.DONE, loaded.items[0].status)
        assertEquals(QueueItemStatus.RUNNING, loaded.items[1].status)
        assertEquals("opened app | typed text", loaded.items[1].progress)
        assertTrue(loaded.hasUnfinished())
        assertNotNull(store.unfinishedSummary(60_000))
    }

    @Test
    fun finishedQueueHasNoReminder() {
        val store = TaskQueueStore(RuntimeEnvironment.getApplication())
        store.save(TaskQueue("t2", "x", listOf(QueueItem("x", QueueItemStatus.DONE))))
        assertFalse(store.hasUnfinished())
        assertNull(store.unfinishedSummary(60_000))
    }
}
