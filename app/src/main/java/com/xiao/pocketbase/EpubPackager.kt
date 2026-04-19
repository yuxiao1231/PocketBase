package com.xiao.pocketbase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// 基础数据结构
data class Chapter(val title: String, val contentHtml: String)
data class BookData(val title: String, val author: String, val chapters: List<Chapter>)

object EpubPackager {

    /**
     * 【核心复用：流式打包】
     * 不管是发给网络还是存入磁盘，最后都走这个方法
     */
    fun packToStream(book: BookData, out: OutputStream) {
        ZipOutputStream(out).use { zos ->
            // 1. 写入 mimetype (必须不压缩，保留你原本的精确逻辑)
            val mimeBytes = "application/epub+zip".toByteArray()
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                crc = getCrc32(mimeBytes)
            }
            zos.putNextEntry(mimeEntry)
            zos.write(mimeBytes)
            zos.closeEntry()

            // 2. 写入 container.xml
            writeZipEntry(zos, "META-INF/container.xml", """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                   <rootfiles>
                      <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                   </rootfiles>
                </container>
            """.trimIndent())

            // 3. 写入章节
            val manifestItems = java.lang.StringBuilder()
            val spineItems = java.lang.StringBuilder()

            book.chapters.forEachIndexed { index, chapter ->
                val fileName = "chapter_${index}.html"
                val htmlContent = """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <head><title>${chapter.title}</title></head>
                    <body><h2>${chapter.title}</h2>${chapter.contentHtml}</body>
                    </html>
                """.trimIndent()
                
                writeZipEntry(zos, "OEBPS/$fileName", htmlContent)
                manifestItems.append("""<item id="chap_$index" href="$fileName" media-type="application/xhtml+xml"/>""")
                spineItems.append("""<itemref idref="chap_$index"/>""")
            }

            // 4. 写入 content.opf
            val opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>${book.title}</dc:title>
                        <dc:creator>${book.author}</dc:creator>
                        <dc:language>zh-CN</dc:language>
                    </metadata>
                    <manifest>$manifestItems</manifest>
                    <spine>$spineItems</spine>
                </package>
            """.trimIndent()
            writeZipEntry(zos, "OEBPS/content.opf", opfContent)
        }
    }

    /**
     * 【便捷入口 1：物理转换】
     * 直接在本地生成实体文件
     */
    fun convertTxtToPhysicalEpub(txtFile: File) {
        val targetFile = File(txtFile.parent, "${txtFile.nameWithoutExtension}.epub")
        val book = simpleTxtToBook(txtFile)
        FileOutputStream(targetFile).use { packToStream(book, it) }
    }

    /**
     * 【便捷入口 2：影子转换】
     * 将 TXT 映射为 BookData，准备对接 Ktor 输出流
     */
    fun simpleTxtToBook(file: File): BookData {
        val estimatedSize = file.length().toInt()
        val builder = java.lang.StringBuilder(estimatedSize + (estimatedSize / 10)) // 预留br标签空间
        
        file.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                builder.append(line).append("<br/>")
            }
        }
        
        return BookData(
            title = file.nameWithoutExtension,
            author = I18n.t("label_unknown_author"), // 顺手把这里的硬编码也做了国际化
            chapters = listOf(Chapter("正文", builder.toString()))
        )
    }

    private fun writeZipEntry(zos: ZipOutputStream, path: String, content: String) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray())
        zos.closeEntry()
    }

    private fun getCrc32(bytes: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(bytes)
        return crc.value
    }
}

/**
 * 【新增模块】：EPUB 逆向提取为 TXT (协程安全 + I18n + 进度反馈)
 */
object EpubExtractor {

    suspend fun batchExtract(
        files: List<File>, 
        onFileProgress: (Int, Int, String) -> Unit,
        onChapterProgress: (Int, Int) -> Unit
    ): List<File> = withContext(Dispatchers.IO) {
        val extractedFiles = mutableListOf<File>()
        
        files.forEachIndexed { index, file ->
            if (file.extension.lowercase() == "epub") {
                onFileProgress(index + 1, files.size, file.name)
                
                try {
                    val result = extractEpubToPhysicalTxt(file, onChapterProgress)
                    if (result != null) extractedFiles.add(result)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return@withContext extractedFiles
    }

    suspend fun extractEpubToPhysicalTxt(
        epubFile: File,
        onChapterProgress: (Int, Int) -> Unit = { _, _ -> }
    ): File? = withContext(Dispatchers.IO) {
        try {
            // 获取语言包后缀
            val suffix = I18n.t("file_suffix_extracted")
            val txtFile = File(epubFile.parentFile, "${epubFile.nameWithoutExtension}${suffix}.txt")
            
            ZipFile(epubFile).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: throw Exception(I18n.t("err_no_container"))
                val containerDoc = Jsoup.parse(zip.getInputStream(containerEntry).bufferedReader().readText(), "", Parser.xmlParser())
                val opfPath = containerDoc.select("rootfile").attr("full-path")
                if (opfPath.isEmpty()) throw Exception(I18n.t("err_no_opf_path"))

                val opfEntry = zip.getEntry(opfPath) ?: throw Exception(I18n.t("err_no_opf_file"))

                val opfDoc = Jsoup.parse(
                    zip.getInputStream(opfEntry).bufferedReader().readText(), 
                    "", 
                    Parser.xmlParser()
                )

                val manifest = opfDoc.select("manifest > item").associate { it.attr("id") to it.attr("href") }
                val spine = opfDoc.select("spine > itemref").mapNotNull { it.attr("idref") }
                val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

                val totalChapters = spine.size

                txtFile.bufferedWriter().use { writer ->
                    val title = opfDoc.select("dc|title").text().ifBlank { epubFile.nameWithoutExtension }
                    val author = opfDoc.select("dc|creator").text().ifBlank { I18n.t("label_unknown_author") }
                    
                    // 头部信息国际化
                    writer.write("========== $title ==========\n")
                    writer.write("========== ${I18n.t("label_author")}: $author ==========\n\n")

                    spine.forEachIndexed { index, id ->
                        val href = manifest[id] ?: return@forEachIndexed
                        val entryName = opfDir + URLDecoder.decode(href, "UTF-8")
                        val htmlEntry = zip.getEntry(entryName) ?: return@forEachIndexed

                        val htmlContent = zip.getInputStream(htmlEntry).bufferedReader().readText()
                        
                        val document = Jsoup.parse(htmlContent)
                        
                        document.select("br").append("{NEWLINE}")
                        document.select("p, div, h1, h2, h3").prepend("{NEWLINE}{NEWLINE}")

                        var cleanText = document.text()
                        
                        cleanText = cleanText.replace("{NEWLINE}", "\n")
                            .replace(Regex("\\n{3,}"), "\n\n") 
                            .trim()

                        if (cleanText.isNotEmpty()) {
                            writer.write(cleanText)
                            writer.write("\n\n")
                        }

                        onChapterProgress(index + 1, totalChapters)
                    }
                }
            }
            return@withContext txtFile
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}