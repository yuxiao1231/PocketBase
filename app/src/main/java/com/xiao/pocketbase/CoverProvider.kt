package com.xiao.pocketbase

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

object CoverProvider {

    // 莫兰迪色系十六进制代码，用于 TXT 纯色封面底色
    private val MORANDI_COLORS = arrayOf(
        "#E8DCD8", "#D1C7C5", "#B8B1B6", "#A5AEB1", "#96A4A5",
        "#C8D2D1", "#DFE1D6", "#EBE2C9", "#D8C9AE", "#C9B4A0"
    )

    /**
     * 获取书籍封面字节流
     * @param file 书籍实体文件
     * @param title 书名（用于 TXT 生成封面）
     * @return 封面的 ByteArray，可以直接写入 HTTP Response
     */
    fun getCoverBytes(file: File, title: String): ByteArray? {
        if (!file.exists()) return null

        return try {
            when (file.extension.lowercase()) {
                "epub" -> extractEpubCover(file)
                "txt" -> generateTxtCover(title)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 方案一：从 EPUB (ZIP) 中逆向提取物理封面
     */
    private fun extractEpubCover(file: File): ByteArray? {
        ZipFile(file).use { zip ->
            // 1. 找到 container.xml，定位 .opf 文件路径
            val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
            val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
            
            // 使用正则快速提取 rootfile 路径
            val opfPathRegex = """full-path="([^"]+\.opf)"""".toRegex()
            val opfPath = opfPathRegex.find(containerXml)?.groupValues?.get(1) ?: return null

            // 2. 读取 .opf 文件内容
            val opfEntry = zip.getEntry(opfPath) ?: return null
            val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()

            // 3. 在 .opf 中寻找封面图片的 href
            // 策略 A: 寻找 properties="cover-image" (EPUB 3 规范)
            var coverHrefRegex = """<item[^>]+href="([^"]+)"[^>]+properties="cover-image"[^>]*>""".toRegex()
            var match = coverHrefRegex.find(opfXml)
            
            if (match == null) {
                // 策略 B: EPUB 2 规范，先找 <meta name="cover" content="ID" />，再找对应 ID 的 item
                val metaCoverRegex = """<meta[^>]+name="cover"[^>]+content="([^"]+)"[^>]*>""".toRegex()
                val coverId = metaCoverRegex.find(opfXml)?.groupValues?.get(1)
                if (coverId != null) {
                    val itemRegex = """<item[^>]+id="$coverId"[^>]+href="([^"]+)"[^>]*>""".toRegex()
                    match = itemRegex.find(opfXml)
                }
            }

            val relativeCoverHref = match?.groupValues?.get(1) ?: return null

            // 4. 解析相对路径（相对于 .opf 所在的目录）
            val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
            var absoluteCoverPath = opfDir + relativeCoverHref
            // 简单处理路径中的 "../" 
            while (absoluteCoverPath.contains("../")) {
                absoluteCoverPath = absoluteCoverPath.replace(Regex("[^/]+/\\.\\./"), "")
            }

            // 5. 提取并返回图片字节流
            val coverImageEntry = zip.getEntry(absoluteCoverPath) ?: return null
            return zip.getInputStream(coverImageEntry).readBytes()
        }
    }

    /**
     * 方案二：为 TXT 动态手绘一张带书名的莫兰迪纯色封面
     */
    private fun generateTxtCover(title: String): ByteArray {
        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. 根据书名的 HashCode 稳定获取一个莫兰迪色，确保同一本书每次生成的颜色一致
        val colorIndex = Math.abs(title.hashCode()) % MORANDI_COLORS.size
        val bgColor = Color.parseColor(MORANDI_COLORS[colorIndex])
        canvas.drawColor(bgColor)

        // 2. 配置绘制文字的画笔 (深灰色，粗体)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#424242")
            textSize = 64f
            typeface = Typeface.DEFAULT_BOLD
        }

        // 3. 使用 StaticLayout 处理长书名的自动换行与居中对齐
        val padding = 60
        val textWidth = width - padding * 2
        
        // 兼容不同 Android 版本的 StaticLayout 构建方式
        val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(title, 0, title.length, textPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.2f)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(title, textPaint, textWidth, Layout.Alignment.ALIGN_CENTER, 1.2f, 0f, false)
        }

        // 4. 计算垂直居中的起始 Y 坐标
        val textHeight = staticLayout.height
        val startY = (height - textHeight) / 2f

        // 5. 移动画布并将文字画上去
        canvas.save()
        canvas.translate(padding.toFloat(), startY)
        staticLayout.draw(canvas)
        canvas.restore()

        // 6. 压入输出流，化为 PNG 字节
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return outputStream.toByteArray()
    }
}