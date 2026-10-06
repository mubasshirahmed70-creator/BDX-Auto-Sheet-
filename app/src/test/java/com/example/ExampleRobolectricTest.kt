package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SheetConfig
import com.example.data.repository.SheetRepository
import com.example.export.XlsxExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var repository: SheetRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = SheetRepository(context)
        repository.resetAllData()
    }

    @Test
    fun `read string from context matches BDX Auto Sheet`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("BDX Auto Sheet", appName)
    }

    @Test
    fun `column parsing ignores empty items and trims whitespace`() {
        val parsed = SheetConfig.parseColumns(" A , , B , C , , ")
        assertEquals(listOf("A", "B", "C"), parsed)
    }

    @Test
    fun `column parsing supports custom names and preserves order`() {
        val parsed = SheetConfig.parseColumns("Email, Password, Name, Phone, Code")
        assertEquals(listOf("Email", "Password", "Name", "Phone", "Code"), parsed)
    }

    @Test
    fun `round logic CASE 1 - sequential columns fill row 1`() {
        // Columns: A, B, C
        repository.updateConfig(SheetConfig(columns = listOf("A", "B", "C")))

        repository.insertFromClipboard("A", "one")
        repository.insertFromClipboard("B", "two")
        repository.insertFromClipboard("C", "three")

        val state = repository.sheetState.value
        assertEquals("one", state.cells[1 to 0])
        assertEquals("two", state.cells[1 to 1])
        assertEquals("three", state.cells[1 to 2])
        assertEquals(1, state.currentRow)
    }

    @Test
    fun `round logic CASE 2 - pressing A again starts next round and skips C1`() {
        // CASE 2: A="one", B="two", A="three" -> A1=one, B1=two, C1=empty, A2=three
        repository.updateConfig(SheetConfig(columns = listOf("A", "B", "C")))

        repository.insertFromClipboard("A", "one")
        repository.insertFromClipboard("B", "two")
        repository.insertFromClipboard("A", "three")

        val state = repository.sheetState.value
        assertEquals("one", state.cells[1 to 0])
        assertEquals("two", state.cells[1 to 1])
        assertNull(state.cells[1 to 2]) // C1 MUST remain empty!
        assertEquals("three", state.cells[2 to 0]) // A2 receives new data!
        assertEquals(2, state.currentRow)
    }

    @Test
    fun `round logic CASE 3 - full sequence with early next round`() {
        // CASE 3: A="one", B="two", A="three", B="four", C="five"
        repository.updateConfig(SheetConfig(columns = listOf("A", "B", "C")))

        repository.insertFromClipboard("A", "one")
        repository.insertFromClipboard("B", "two")
        repository.insertFromClipboard("A", "three")
        repository.insertFromClipboard("B", "four")
        repository.insertFromClipboard("C", "five")

        val state = repository.sheetState.value
        assertEquals("one", state.cells[1 to 0])
        assertEquals("two", state.cells[1 to 1])
        assertNull(state.cells[1 to 2])
        assertEquals("three", state.cells[2 to 0])
        assertEquals("four", state.cells[2 to 1])
        assertEquals("five", state.cells[2 to 2])
        assertEquals(2, state.currentRow)
    }

    @Test
    fun `round logic CASE 4 - column skipping is preserved`() {
        // CASE 4: Columns A, B, C, D. A, B, D -> A1, B1, C1 empty, D1.
        // Then A, C -> A2, C2.
        repository.updateConfig(SheetConfig(columns = listOf("A", "B", "C", "D")))

        repository.insertFromClipboard("A", "dataA1")
        repository.insertFromClipboard("B", "dataB1")
        repository.insertFromClipboard("D", "dataD1")

        var state = repository.sheetState.value
        assertEquals("dataA1", state.cells[1 to 0])
        assertEquals("dataB1", state.cells[1 to 1])
        assertNull(state.cells[1 to 2]) // C1 empty
        assertEquals("dataD1", state.cells[1 to 3])

        // Then A, C
        repository.insertFromClipboard("A", "dataA2")
        repository.insertFromClipboard("C", "dataC2")

        state = repository.sheetState.value
        assertEquals("dataA2", state.cells[2 to 0])
        assertNull(state.cells[2 to 1]) // B2 empty
        assertEquals("dataC2", state.cells[2 to 2])
        assertNull(state.cells[2 to 3]) // D2 empty
    }

    @Test
    fun `undo and redo maintain exact cell data and round row state`() {
        repository.updateConfig(SheetConfig(columns = listOf("A", "B", "C")))

        repository.insertFromClipboard("A", "AAA")
        assertEquals(1, repository.sheetState.value.currentRow)
        assertEquals("AAA", repository.sheetState.value.cells[1 to 0])

        assertTrue(repository.canUndo.value)
        repository.undo()

        assertNull(repository.sheetState.value.cells[1 to 0])
        assertEquals(1, repository.sheetState.value.currentRow)

        assertTrue(repository.canRedo.value)
        repository.redo()

        assertEquals("AAA", repository.sheetState.value.cells[1 to 0])
    }

    @Test
    fun `reset all clears cell data but preserves column configuration`() {
        repository.updateConfig(SheetConfig(columns = listOf("Col1", "Col2", "Col3")))
        repository.insertFromClipboard("Col1", "val1")
        repository.insertFromClipboard("Col2", "val2")

        assertEquals(2, repository.sheetState.value.totalFilledCells)

        repository.resetAllData()

        val state = repository.sheetState.value
        assertEquals(0, state.totalFilledCells)
        assertEquals(1, state.currentRow)
        assertEquals(listOf("Col1", "Col2", "Col3"), state.config.columns)
    }

    @Test
    fun `xlsx export generates valid OpenXml zip archive`() {
        repository.updateConfig(SheetConfig(columns = listOf("A", "B", "C")))
        repository.insertFromClipboard("A", "Test1")
        repository.insertFromClipboard("B", "Test2")

        val state = repository.sheetState.value
        val baos = ByteArrayOutputStream()
        XlsxExporter.exportToStream(state, baos)

        val bytes = baos.toByteArray()
        assertTrue("XLSX output should not be empty", bytes.isNotEmpty())

        // Verify ZIP entries
        val zis = ZipInputStream(bytes.inputStream())
        val entryNames = mutableSetOf<String>()
        var entry = zis.nextEntry
        while (entry != null) {
            entryNames.add(entry.name)
            entry = zis.nextEntry
        }

        assertTrue(entryNames.contains("[Content_Types].xml"))
        assertTrue(entryNames.contains("_rels/.rels"))
        assertTrue(entryNames.contains("xl/workbook.xml"))
        assertTrue(entryNames.contains("xl/_rels/workbook.xml.rels"))
        assertTrue(entryNames.contains("xl/styles.xml"))
        assertTrue(entryNames.contains("xl/worksheets/sheet1.xml"))
    }
}
