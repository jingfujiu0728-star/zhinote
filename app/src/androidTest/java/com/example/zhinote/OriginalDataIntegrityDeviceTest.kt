package com.example.zhinote

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zhinote.data.local.ZhiDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class OriginalDataIntegrityDeviceTest {
    @Test fun everyPreUpgradeRecordStillExistsUnchanged() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        assumeTrue("Private original snapshot not included in shared source", assets.list("databases")?.contains("zhinote.db") == true)
        val filename = "original-integrity-${System.nanoTime()}.db"
        val file = app.getDatabasePath(filename); file.parentFile!!.mkdirs()
        assets.open("databases/zhinote.db").use { input -> file.outputStream().use { input.copyTo(it) } }
        assets.open("databases/zhinote.db-wal").use { input -> java.io.File(file.path + "-wal").outputStream().use { input.copyTo(it) } }
        val old = Room.databaseBuilder(app, ZhiDatabase::class.java, filename).addMigrations(ZhiDatabase.MIGRATION_1_2).build()
        try {
            val baseline = old.dao(); val current = app.database.dao()
            baseline.allNotes().forEach { assertEquals("Existing note must remain unchanged", it, current.note(it.id)) }
            baseline.allTodos().forEach { assertEquals("Existing todo/reminder/statistics must remain unchanged", it, current.todo(it.id)) }
            val categories = current.allCategories().associateBy { it.id }
            baseline.allCategories().forEach { assertEquals(it, categories[it.id]) }
            val layouts = current.allLayouts().associateBy { it.itemType to it.itemId }
            baseline.allLayouts().forEach { assertEquals(it, layouts[it.itemType to it.itemId]) }
            println("Original records verified: notes=${baseline.allNotes().size}, todos=${baseline.allTodos().size}, categories=${baseline.allCategories().size}, layouts=${baseline.allLayouts().size}")
        } finally { old.close(); app.deleteDatabase(filename) }
    }
}
