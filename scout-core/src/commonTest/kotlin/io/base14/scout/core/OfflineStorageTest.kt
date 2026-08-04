package io.base14.scout.core

import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineStorageTest {

    private fun FakeFileSystem.writeFile(path: Path, bytes: Int) {
        write(path) { write(ByteArray(bytes)) }
    }

    private fun FakeFileSystem.totalSize(dir: Path): Long =
        list(dir).sumOf { metadata(it).size ?: 0L }

    @Test
    fun evictsOldestFirstUntilUnderCap() {
        val fs = FakeFileSystem()
        val dir = "/offline".toPath()
        fs.createDirectories(dir)
        // 5 files x 300 bytes = 1500; cap 1000 -> must evict oldest until <= 1000.
        val files = (1..5).map { dir / "f$it.jsonl" }
        for (f in files) fs.writeFile(f, 300)

        pruneDirToCap(fs, dir, capBytes = 1000)

        assertTrue(fs.totalSize(dir) <= 1000, "total must be under cap")
        assertEquals(3, fs.list(dir).size, "1500->must drop 2 (600) to reach 900")
        assertFalse(fs.exists(files[0]), "oldest f1 evicted")
        assertFalse(fs.exists(files[1]), "next-oldest f2 evicted")
        assertTrue(fs.exists(files[2]), "f3 kept")
        assertTrue(fs.exists(files[4]), "newest f5 kept")
    }

    @Test
    fun noEvictionWhenUnderCap() {
        val fs = FakeFileSystem()
        val dir = "/offline".toPath()
        fs.createDirectories(dir)
        val f = dir / "a.jsonl"
        fs.writeFile(f, 500)

        pruneDirToCap(fs, dir, capBytes = 1000)

        assertTrue(fs.exists(f), "file under cap must be kept")
    }

    @Test
    fun capZeroIsNoop() {
        val fs = FakeFileSystem()
        val dir = "/offline".toPath()
        fs.createDirectories(dir)
        val f = dir / "a.jsonl"
        fs.writeFile(f, 500)

        pruneDirToCap(fs, dir, capBytes = 0)

        assertTrue(fs.exists(f), "cap<=0 must not delete anything")
    }

    @Test
    fun missingDirIsNoop() {
        val fs = FakeFileSystem()
        pruneDirToCap(fs, "/does-not-exist".toPath(), capBytes = 1000)
    }
}
