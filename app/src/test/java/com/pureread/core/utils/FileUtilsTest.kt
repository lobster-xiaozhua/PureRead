package com.pureread.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [FileUtils] 单元测试。
 */
internal class FileUtilsTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `ensureDir creates missing directory`() {
        val dir = temporaryFolder.newFolder().resolve("missing/nested")

        val result = FileUtils.ensureDir(dir)

        assertTrue(result)
        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `ensureDir returns true for existing directory`() {
        val dir = temporaryFolder.newFolder()

        val result = FileUtils.ensureDir(dir)

        assertTrue(result)
    }

    @Test
    fun `writeTwoPhaseCommit creates target file atomically`() {
        val targetFile = temporaryFolder.newFolder().resolve("article.txt")
        val content = "PureRead 正文内容"

        val result = FileUtils.writeTwoPhaseCommit(targetFile, content)

        assertTrue(result)
        assertTrue(targetFile.exists())
        assertEquals(content, targetFile.readText())
        assertFalse(targetFile.parentFile!!.listFiles()?.any { it.name.endsWith(".tmp") } ?: false)
    }

    @Test
    fun `writeTwoPhaseCommit overwrites existing file`() {
        val targetFile = temporaryFolder.newFile("article.txt")
        targetFile.writeText("旧内容")

        val result = FileUtils.writeTwoPhaseCommit(targetFile, "新内容")

        assertTrue(result)
        assertEquals("新内容", targetFile.readText())
    }

    @Test
    fun `deleteFile removes file recursively`() {
        val dir = temporaryFolder.newFolder("novels")
        val file = dir.resolve("chapter.txt")
        file.writeText("章节")

        val result = FileUtils.deleteFile(dir.absolutePath)

        assertTrue(result)
        assertFalse(dir.exists())
    }

    @Test
    fun `deleteFile returns true for nonexistent path`() {
        val path = temporaryFolder.root.resolve("nonexistent").absolutePath

        val result = FileUtils.deleteFile(path)

        assertTrue(result)
    }

    @Test
    fun `deleteFile returns false for blank path`() {
        val result = FileUtils.deleteFile(" ")

        assertFalse(result)
    }
}
