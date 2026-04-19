package com.xiao.pocketbase

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ShortcutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 初始化国际化，确保 Toast 语言正确
        I18n.init(context)

        when (intent.action) {
            "com.xiao.pocketbase.ACTION_START_OPDS" -> {
                OpdsServer.start()
                Toast.makeText(context, I18n.t("toast_opds_started"), Toast.LENGTH_SHORT).show()
            }
            "com.xiao.pocketbase.ACTION_STOP_OPDS" -> {
                Toast.makeText(context, I18n.t("toast_opds_stopped"), Toast.LENGTH_SHORT).show()
                OpdsServer.stop()
                
                // 延迟一秒给 Toast 留点生存时间，然后自尽
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(0)
                }, 1000)
            }
        }
    }
}