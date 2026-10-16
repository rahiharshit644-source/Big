package com.soltini.app.agent

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskSplitterTest {

    @Test
    fun splitsOnStrongSequenceWords() {
        val parts = TaskSplitter.heuristicSplit("WhatsApp pe Rahul ko hi bhejo phir kal ka alarm lagao phir Tum Hi Ho song chalao")
        assertEquals(3, parts.size)
        assertEquals("kal ka alarm lagao", parts[1])

        assertEquals(2, TaskSplitter.heuristicSplit("Open YouTube then search lofi").size)
        assertEquals(listOf("Rahul ko msg bhejo", "Aman ko call karo"),
            TaskSplitter.heuristicSplit("Rahul ko msg bhejo, phir Aman ko call karo"))
    }

    @Test
    fun splitsNumberedLists() {
        val parts = TaskSplitter.heuristicSplit("1. Wifi on karo\n2. Bluetooth off karo\n3. Torch chalao")
        assertEquals(listOf("Wifi on karo", "Bluetooth off karo", "Torch chalao"), parts)
    }

    @Test
    fun singleTasksStaySingle() {
        assertEquals(1, TaskSplitter.heuristicSplit("salt aur pepper search karo").size)
        assertFalse(TaskSplitter.mightBeMultiTask("YouTube par song chalao"))
        assertFalse(TaskSplitter.mightBeMultiTask("salt aur pepper search karo"))
    }

    @Test
    fun longRequestsWithSoftConnectorsAskTheModel() {
        assertTrue(TaskSplitter.mightBeMultiTask("Rahul ko message bhejo aur mummy ko call lagao aur alarm set karo"))
    }

    @Test
    fun modelReplyParsing() {
        assertEquals(listOf("a b c", "do d"), TaskSplitter.parseModelList("```json\n[\"a b c\", \"do d\"]\n```"))
        assertTrue(TaskSplitter.parseModelList("not json").isEmpty())
    }
}
