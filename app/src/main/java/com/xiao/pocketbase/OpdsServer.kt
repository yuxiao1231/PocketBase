package com.xiao.pocketbase

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.application.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder

object OpdsServer {
    private var server: NettyApplicationEngine? = null
    // 使用专门的后台域管理服务器
    private val serverScope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (server != null) return

        serverScope.launch {
            try {
                server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
                    routing {
                        get("/opds") {
                            val xml = generateOpdsXml(BookRepository.getBooks())
                            call.respondText(xml, ContentType.parse("application/atom+xml; charset=utf-8"))
                        }

                        get("/download/{fileName}") {
                            val fileName = call.parameters["fileName"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                            val file = File(BookRepository.getRootPath(), fileName)
                            if (!file.exists()) return@get call.respond(HttpStatusCode.NotFound)

                            val isShadowAction = file.extension.lowercase() == "txt" && BookRepository.isShadowConvertEnabled
                            if (isShadowAction) {
                                call.respondOutputStream(ContentType.parse("application/epub+zip")) {
                                    val bookData = EpubPackager.simpleTxtToBook(file)
                                    EpubPackager.packToStream(bookData, this)
                                }
                            } else {
                                call.respondFile(file)
                            }
                        }

                        get("/cover/{fileName}") {
                            val fileName = call.parameters["fileName"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                            val book = BookRepository.getBooks().find { it.first.name == fileName }
                            val coverUrl = book?.second?.coverUrl

                            if (!coverUrl.isNullOrEmpty()) {
                                call.respondRedirect(coverUrl, permanent = false)
                            } else {
                                call.respond(HttpStatusCode.NotFound)
                            }
                        }
                    }
                }
                server?.start(wait = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateOpdsXml(books: List<Pair<File, BookMetadataEntity>>): String {
        val sb = java.lang.StringBuilder()
        // 【I18n 接入】：让 OPDS 根标题动态化
        val feedTitle = I18n.t("app_name") 
        
        sb.append("""<?xml version="1.0" encoding="UTF-8"?><feed xmlns="http://www.w3.org/2005/Atom">
            <id>urn:uuid:xiao-pocketbase</id><title>$feedTitle</title>
            <updated>${System.currentTimeMillis()}</updated>""")

        // 【I18n 接入】：获取影子标识
        val shadowTag = I18n.t("tag_shadow")

        for ((file, meta) in books) {
            val name = file.nameWithoutExtension
            val ext = file.extension.lowercase()
            val useShadow = ext == "txt" && BookRepository.isShadowConvertEnabled

            val displayMime = if (useShadow) "application/epub+zip" else when (ext) {
                "epub" -> "application/epub+zip"
                "mobi" -> "application/x-mobipocket-ebook"
                else -> "text/plain"
            }
            
            // 【I18n 接入】：动态拼接影子标题
            val displayTitle = if (useShadow) "$shadowTag ${meta.title.ifBlank { name }}" else meta.title

            sb.append("""
                <entry>
                    <title>$displayTitle</title>
                    <updated>${file.lastModified()}</updated>
                    <link rel="http://opds-spec.org/acquisition" 
                          href="/download/${URLEncoder.encode(file.name, "UTF-8")}" 
                          type="$displayMime"/>
                    <link rel="http://opds-spec.org/image" 
                          href="/cover/${URLEncoder.encode(file.name, "UTF-8")}"/>
                </entry>
            """)
        }
        sb.append("</feed>")
        return sb.toString()
    }

    fun stop() { server?.stop(1000, 2000); server = null }
}