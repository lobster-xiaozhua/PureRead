package com.pureread.core.utils

import com.pureread.core.log.PureLog
import java.io.File
import java.io.IOException

/**
 * 文件操作工具单例。
 *
 * 职责：
 * - 保证目录存在
 * - 提供两阶段提交写入（.tmp → 原子 renameTo → .txt），避免写入过程中断导致文件损坏
 *
 * 线程安全：无状态对象；对同一文件并发写入需由调用方同步。
 */
public object FileUtils {

    private const val TEMP_FILE_SUFFIX = ".tmp"
    private const val TARGET_FILE_SUFFIX = ".txt"

    /**
     * 确保目录存在，不存在则创建。
     *
     * 前置条件：dir 不为 null。
     * 后置条件：目录存在且为目录时返回 true。
     * 副作用：可能在文件系统创建目录。
     *
     * @param dir 目标目录
     * @return 目录可用返回 true，否则 false
     */
    public fun ensureDir(dir: File): Boolean {
        return when {
            dir.exists() && dir.isDirectory -> true
            dir.mkdirs() -> true
            else -> {
                PureLog.e("FileUtils", "ensureDir", "目录创建失败 path=${dir.absolutePath}")
                false
            }
        }
    }

    /**
     * 确保目录存在（兼容旧命名）。
     *
     * @param dir 目标目录
     * @return 目录可用返回 true，否则 false
     */
    public fun ensureDirectory(dir: File): Boolean = ensureDir(dir)

    /**
     * 删除文件或目录。
     *
     * @param pathString 文件或目录路径
     * @return 删除成功返回 true，否则 false
     */
    public fun deleteFile(pathString: String): Boolean {
        if (pathString.isBlank()) return false
        return try {
            val file = File(pathString)
            if (!file.exists()) {
                return true
            }
            val isDeleted = file.deleteRecursively()
            if (!isDeleted) {
                PureLog.e("FileUtils", "deleteFile", "删除失败 path=$pathString")
            }
            isDeleted
        } catch (e: SecurityException) {
            PureLog.e("FileUtils", "deleteFile", e, "权限异常 path=$pathString")
            false
        }
    }

    /**
     * 两阶段提交写入文本文件。
     *
     * 前置条件：targetFile 父目录存在或可被创建；contentString 非空。
     * 后置条件：targetFile 存在且内容完整；临时文件已被删除。
     * 副作用：在文件系统创建/覆盖文件。
     *
     * @param targetFile  目标文件（最终应以 .txt 结尾）
     * @param contentString 待写入内容
     * @return 写入成功返回 true，否则 false
     */
    public fun writeTwoPhaseCommit(targetFile: File, contentString: String): Boolean {
        // 前置条件：确保父目录存在
        val parentDir = targetFile.parentFile
        if (parentDir != null && !ensureDir(parentDir)) {
            return false
        }

        val tempFile = File(parentDir, "${targetFile.name}$TEMP_FILE_SUFFIX")
        return try {
            tempFile.writeText(contentString, Charsets.UTF_8)
            val isFlushed = tempFile.exists() && tempFile.length() > 0L || contentString.isEmpty()
            if (!isFlushed) {
                PureLog.e("FileUtils", "writeTwoPhaseCommit", "临时文件写入未落盘 path=${tempFile.absolutePath}")
                return false
            }

            val isRenamed = tempFile.renameTo(targetFile)
            if (!isRenamed) {
                PureLog.e(
                    "FileUtils",
                    "writeTwoPhaseCommit",
                    "renameTo 失败 tmp=${tempFile.absolutePath} target=${targetFile.absolutePath}"
                )
                false
            } else {
                PureLog.d("FileUtils", "writeTwoPhaseCommit", "完成 path=${targetFile.absolutePath}")
                true
            }
        } catch (e: IOException) {
            PureLog.e("FileUtils", "writeTwoPhaseCommit", e, "写入异常 path=${targetFile.absolutePath}")
            false
        } catch (e: SecurityException) {
            PureLog.e("FileUtils", "writeTwoPhaseCommit", e, "权限异常 path=${targetFile.absolutePath}")
            false
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
