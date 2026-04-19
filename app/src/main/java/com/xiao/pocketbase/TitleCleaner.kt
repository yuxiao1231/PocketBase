package com.xiao.pocketbase

import java.util.Locale

object TitleCleaner {
    
    /**
     * 【国际化增强版】终极标题清洗
     * 智能识别多语言“牛皮癣”标签并进行无损剥离
     */
    fun clean(fileName: String): String {
        // 判断是否为西方语境
        val isWestern = Locale.getDefault().language == "en"

        var cleanName = fileName
            // 1. 斩断后缀 (通用：增加 azw3 和 pdf 防御)
            .replace(Regex("\\.(txt|epub|mobi|azw3|pdf)$", RegexOption.IGNORE_CASE), "") 
            
            // 2. 去除各类括号及其内容 (通用：补充全角圆括号 `（）`)
            // 注意：这会干掉类似 [精校版], (Original Edition), 【完结】 等标签
            .replace(Regex("《|》|\\[.*?\\]|【.*?】|\\(.*?\\)|（.*?）"), "") 
            
            // 3. 去除作者声明 (多语言融合：支持 作者：, Author:, By )
            .replace(Regex("(?i)(作者|Author|By)[：:\\s].*?($|\\s)"), "")

        // 4. 语境化清洗“牛皮癣”词汇
        cleanName = if (isWestern) {
            // 英文牛皮癣：完整版、零售版、未删减版、卷号等
            cleanName.replace(Regex("(?i)\\b(Unabridged|Edited|Complete|Retail|Web Novel|Vol\\.?\\d*|Volume\\s*\\d*)\\b"), "")
                .replace(Regex("\\s\\-\\s.*$"), "") // 英文常带 " - Subtitle" 的后缀，切掉它匹配更准
        } else {
            // 中日文牛皮癣：TXT下载、实体书、Web版等
            cleanName.replace(Regex("精校版|校对版|完结版|完本|最新章节|全本|TXT下载|实体书|Web版|文库版"), "")
        }

        // 5. 格式抹平：干掉多余的连接符和连续空格
        return cleanName
            .replace(Regex("[_]"), " ")     // 下划线转空格
            .replace(Regex("\\s{2,}"), " ") // 多个空格合并为一个
            .trim()
    }
}