package com.soltini.app.messaging

import com.soltini.app.messaging.BusyContactPolicy.ConfirmKind
import org.junit.Assert.*
import org.junit.Test

class BusyContactPolicyTest {

    @Test
    fun groupsAreDetected() {
        // WhatsApp group: "Sender: message"
        assertTrue(BusyContactPolicy.looksLikeGroup("College Group", null, false, emptyList(), "Rahul: kal class hai"))
        // explicit Android group flag / conversation title
        assertTrue(BusyContactPolicy.looksLikeGroup("Anything", null, true, emptyList(), "hello"))
        assertTrue(BusyContactPolicy.looksLikeGroup("Anything", "CSE 2026", false, emptyList(), "hello"))
        // Telegram style
        assertTrue(BusyContactPolicy.looksLikeGroup("Rahul @ Study Group", null, false, emptyList(), "hello"))
        // other sender than chat title
        assertTrue(BusyContactPolicy.looksLikeGroup("Friends", null, false, listOf("Aman"), "hello"))
    }

    @Test
    fun oneToOneChatsAreNotGroups() {
        assertFalse(BusyContactPolicy.looksLikeGroup("Mummy", null, false, emptyList(), "beta khana khaya?"))
        assertFalse(BusyContactPolicy.looksLikeGroup("Mummy (3 messages)", null, false, listOf("Mummy"), "beta khana khaya?"))
        assertFalse(BusyContactPolicy.looksLikeGroup("Rahul", null, false, listOf("Rahul"), "bhai kal milte hain"))
    }

    @Test
    fun familyIsImportant() {
        assertEquals("mummy", BusyContactPolicy.importanceFor("Mummy ❤️"))
        assertEquals("papa", BusyContactPolicy.importanceFor("Papa"))
        assertEquals("bhai", BusyContactPolicy.importanceFor("Bhai Rohit"))
        assertEquals("behen", BusyContactPolicy.importanceFor("Didi"))
        assertNull(BusyContactPolicy.importanceFor("Rahul"))
        assertEquals("important", BusyContactPolicy.importanceFor("Rahul", setOf("rahul")))
    }

    @Test
    fun cleanContactNameStripsMessageCount() {
        assertEquals("Mummy", BusyContactPolicy.cleanContactName("Mummy (3 messages)"))
        assertEquals("Rahul", BusyContactPolicy.cleanContactName("Rahul"))
    }

    @Test
    fun spokenConfirmation() {
        assertEquals(ConfirmKind.SEND, BusyContactPolicy.parseConfirmation("bhej do")?.kind)
        assertEquals(ConfirmKind.SEND, BusyContactPolicy.parseConfirmation("haan bhej do")?.kind)
        assertEquals(ConfirmKind.SEND, BusyContactPolicy.parseConfirmation("reply kar do")?.kind)
        val named = BusyContactPolicy.parseConfirmation("mummy ko bhej do")
        assertEquals(ConfirmKind.SEND, named?.kind)
        assertEquals("mummy", named?.name)
        assertEquals(ConfirmKind.SKIP, BusyContactPolicy.parseConfirmation("skip")?.kind)
        assertEquals(ConfirmKind.SKIP, BusyContactPolicy.parseConfirmation("reply mat karo")?.kind)
    }

    @Test
    fun normalCommandsAreNotConfirmations() {
        assertNull(BusyContactPolicy.parseConfirmation("Rahul ko message bhejo ki kal aana"))
        assertNull(BusyContactPolicy.parseConfirmation("mummy ko call kar do"))
        assertNull(BusyContactPolicy.parseConfirmation("kal subah 6 baje alarm lagao"))
        assertNull(BusyContactPolicy.parseConfirmation("YouTube par song chalao"))
    }
}
