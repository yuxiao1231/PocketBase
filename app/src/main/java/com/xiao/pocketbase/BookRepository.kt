package com.xiao.pocketbase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BookRepository {
    var isShadowConvertEnabled = false
    
    // 默认指向系统书籍目录，你也可以在 UI 里随时改到 Legado 目录
    private var rootPath = "/storage/emulated/0/books" 
    private var cachedBooks: List<Pair<File, BookMetadataEntity>> = emptyList()

    fun getRootPath() = rootPath
    fun getBooks() = cachedBooks

    suspend fun updateRootPath(newPath: String, onLog: ((String) -> Unit)? = null) {
        rootPath = newPath
        refresh(onLog)
    }

    suspend fun refresh(onLog: ((String) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val dir = File(rootPath)
        
        onLog?.invoke(I18n.t("log_repo_diag_start", dir.absolutePath))
        
        if (!dir.exists()) {
            onLog?.invoke(I18n.t("log_repo_dir_missing"))
            try { dir.mkdirs() } catch (e: Exception) { 
                onLog?.invoke(I18n.t("log_repo_create_fail", e.message ?: "Unknown")) 
            }
            cachedBooks = emptyList()
            return@withContext
        }

        if (!dir.isDirectory) {
            onLog?.invoke(I18n.t("log_repo_not_dir"))
            cachedBooks = emptyList()
            return@withContext
        }

        onLog?.invoke(I18n.t("log_repo_dir_ok"))

        // 【核心修复】：使用 walkTopDown() 进行无限极递归扫描
        val validExtensions = listOf("epub", "mobi", "txt")
        val files = dir.walkTopDown()
            .filter { f -> 
                f.isFile && 
                f.extension.lowercase() in validExtensions &&
                !f.name.startsWith(".") // 过滤掉隐藏文件 (重要)
            }
            .toList()
            .sortedByDescending { it.lastModified() }

        if (files.isEmpty()) {
            // 将扩展名列表转为字符串传入占位符
            onLog?.invoke(I18n.t("log_repo_warn_empty", validExtensions.joinToString("/")))
        } else {
            onLog?.invoke(I18n.t("log_repo_scan_done", files.size))
        }

        cachedBooks = MetadataManager.getBookInfosBatch(files)
        
        onLog?.invoke(I18n.t("log_repo_map_done", cachedBooks.size))
    }
}