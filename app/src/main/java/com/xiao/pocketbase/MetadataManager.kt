package com.xiao.pocketbase

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Entity(tableName = "book_metadata")
data class BookMetadataEntity(
    @PrimaryKey val fileName: String,
    val title: String,
    val author: String,
    val summary: String,
    val coverUrl: String?,
    val isMatched: Boolean = false
)

@Dao
interface MetadataDao {
    @Query("SELECT * FROM book_metadata WHERE fileName = :name LIMIT 1")
    suspend fun getByFileName(name: String): BookMetadataEntity?

    @Query("SELECT * FROM book_metadata WHERE fileName IN (:names)")
    suspend fun getByFileNames(names: List<String>): List<BookMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: BookMetadataEntity)

    @Query("DELETE FROM book_metadata WHERE fileName = :name")
    suspend fun deleteByFileName(name: String)
}

@Database(entities = [BookMetadataEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun metadataDao(): MetadataDao
}

object MetadataManager {
    private lateinit var db: AppDatabase

    fun init(context: Context) {
        db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pocket_base_v4")
            .fallbackToDestructiveMigration()
            .build()
    }

    suspend fun getBookInfo(
        file: File, 
        autoFetch: Boolean = false, 
        source: SearchSource = SearchSource.AUTO
    ): BookMetadataEntity {
        val cached = db.metadataDao().getByFileName(file.name)
        if (cached != null && !autoFetch) return cached

        if (autoFetch) {
            try {
                val remote = BookMetadataService.getIntegratedMetadata(file, source)
                val entity = BookMetadataEntity(
                    fileName = file.name,
                    title = remote.title,
                    author = remote.author,
                    summary = remote.summary,
                    coverUrl = remote.coverUrl,
                    isMatched = remote.isMatched
                )
                db.metadataDao().insert(entity)
                return entity
            } catch (e: Exception) { e.printStackTrace() }
        }
        
        // 【I18n 接入】替换硬编码
        return cached ?: BookMetadataEntity(
            file.name, 
            file.nameWithoutExtension, 
            I18n.t("label_unknown_author"), 
            I18n.t("label_no_summary"), 
            null, 
            false
        )
    }

    suspend fun getBookInfosBatch(files: List<File>): List<Pair<File, BookMetadataEntity>> {
        val fileNames = files.map { it.name }
        val cachedList = db.metadataDao().getByFileNames(fileNames)
        val cacheMap = cachedList.associateBy { it.fileName }
        return files.map { file ->
            // 【I18n 接入】替换硬编码
            val cached = cacheMap[file.name] ?: BookMetadataEntity(
                file.name, 
                file.nameWithoutExtension, 
                I18n.t("label_unknown_author"), 
                I18n.t("label_no_summary"), 
                null, 
                false
            )
            Pair(file, cached)
        }
    }

    /**
     * 【安全重构】物理改名执行器
     * 绝不删除任何文件！如果目标名冲突，则自动加数字后缀。
     */
    suspend fun sanitizePhysicalFile(file: File, meta: BookMetadataEntity): String? = withContext(Dispatchers.IO) {
        val ext = file.extension.lowercase()
        // 这里的 meta.title 已经由上面的逻辑注入了 [合集] 标签且去掉了冗余括号
        val safeTitle = meta.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val safeAuthor = meta.author.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        
        val baseNewName = "$safeTitle - $safeAuthor"
        var finalNewName = "$baseNewName.$ext"
        
        if (file.name == finalNewName) return@withContext null
        
        var targetFile = File(file.parent, finalNewName)
        var count = 1
        
        // 防冲突 While 循环
        while (targetFile.exists() && targetFile.absolutePath != file.absolutePath) {
            finalNewName = "$baseNewName ($count).$ext"
            targetFile = File(file.parent, finalNewName)
            count++
        }
        
        if (file.exists()) {
            if (file.renameTo(targetFile)) {
                db.metadataDao().deleteByFileName(file.name)
                db.metadataDao().insert(meta.copy(fileName = finalNewName))
                return@withContext finalNewName
            }
        }
        null
    }
    
    suspend fun sanitizePhysicalFile(file: File, remote: BookMetadata): String? {
        val entity = BookMetadataEntity(file.name, remote.title, remote.author, remote.summary, remote.coverUrl, remote.isMatched)
        return sanitizePhysicalFile(file, entity)
    }
}