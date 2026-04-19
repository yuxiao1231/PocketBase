package com.xiao.pocketbase

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvIp: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPath: TextView
    private lateinit var tvConsole: TextView
    private lateinit var rvBookList: RecyclerView
    private lateinit var scrollConsole: ScrollView
    private lateinit var bookAdapter: BookAdapter

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val path = resolveUriToPath(uri)
                if (path != null) {
                    tvPath.text = path
                    lifecycleScope.launch {
                        BookRepository.updateRootPath(path)
                        refreshBookListUI()
                        logToConsole(I18n.t("log_path_changed", path))
                        Toast.makeText(this@MainActivity, I18n.t("toast_path_updated"), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, I18n.t("toast_path_error"), Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        
        // 【关键】初始化语言包，必须在加载 UI 和 Service 前完成！
        I18n.init(applicationContext)
        MetadataManager.init(applicationContext)
        
        setContentView(R.layout.activity_main)

        tvIp = findViewById(R.id.tv_ip)
        tvStatus = findViewById(R.id.tv_status)
        tvPath = findViewById(R.id.tv_current_path)
        tvConsole = findViewById(R.id.tv_console)
        rvBookList = findViewById(R.id.rv_book_list)
        scrollConsole = findViewById(R.id.scroll_console)

        val swTheme = findViewById<MaterialSwitch>(R.id.switch_theme)
        val swService = findViewById<MaterialSwitch>(R.id.switch_service)
        val swSplit = findViewById<MaterialSwitch>(R.id.switch_auto_split)

        val btnMatchMeta = findViewById<Button>(R.id.btn_match_meta)
        val btnChangePath = findViewById<Button>(R.id.btn_change_path)
        val btnHelp = findViewById<ImageButton>(R.id.btn_help)
        val btnBatchConvert = findViewById<Button>(R.id.btn_batch_convert)
        val btnExtractTxt = findViewById<Button>(R.id.btn_extract_txt)

        // 【UI 控件文本国际化覆盖】防止 XML 里的硬编码泄漏
        swTheme.text = I18n.t("ui_switch_theme")
        swService.text = I18n.t("ui_switch_service")
        swSplit.text = I18n.t("ui_switch_shadow")
        btnMatchMeta.text = I18n.t("ui_btn_match")
        btnChangePath.text = I18n.t("ui_btn_change_path")
        btnBatchConvert.text = I18n.t("ui_btn_convert")
        btnExtractTxt.text = I18n.t("ui_btn_extract")
        findViewById<TextView>(R.id.tv_app_title).text = I18n.t("app_name_full")
        findViewById<TextView>(R.id.tv_path_label).text = I18n.t("ui_path_label")
        findViewById<TextView>(R.id.tv_advanced_title).text = I18n.t("ui_advanced_title")
        findViewById<TextView>(R.id.tv_library_title).text = I18n.t("ui_library_title")
        findViewById<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton>(R.id.fab_refresh).text = I18n.t("ui_fab_refresh")

        tvStatus.text = I18n.t("status_stopped")
        tvStatus.setTextColor(0xFFFF9800.toInt()) // 默认显示为橙色警告
        tvIp.text = I18n.t("ip_koreader_disabled")

        bookAdapter = BookAdapter()
        rvBookList.adapter = bookAdapter

        checkPermissions()

        swTheme.isChecked = isDarkMode
        val isSplitEnabled = prefs.getBoolean("auto_split", false)
        swSplit.isChecked = isSplitEnabled
        BookRepository.isShadowConvertEnabled = isSplitEnabled
        tvPath.text = BookRepository.getRootPath()

        if (savedInstanceState == null) {
            doRefreshAction()
        } else {
            refreshBookListUI()
        }

        swTheme.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        btnChangePath.setOnClickListener { folderPickerLauncher.launch(null) }

        // 【核心改进】动态加载语言菜单
        val btnLanguage = findViewById<ImageButton>(R.id.btn_language)
        btnLanguage.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            val availableLangs = I18n.getAvailableLanguages(this)
            
            // 1. 添加“跟随系统”选项，ID 设为 -1
            popup.menu.add(0, -1, 0, "${I18n.t("ui_lang_auto")} (Auto)")

            // 2. 遍历语言包 Map，动态添加菜单项
            val langTags = availableLangs.keys.toList()
            langTags.forEachIndexed { index, tag ->
                val displayName = availableLangs[tag] ?: tag
                // itemId 设置为 index，用于后面从 langTags 找回对应 Tag
                popup.menu.add(0, index, index + 1, displayName)
            }

            popup.setOnMenuItemClickListener { item ->
                val langTag = if (item.itemId == -1) {
                    "auto"
                } else {
                    langTags[item.itemId]
                }

                // 写入配置并重启应用
                prefs.edit().putString("forced_language", langTag).apply()
                I18n.init(applicationContext)
                recreate() 
                true
            }
            popup.show()
        }

        swSplit.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_split", isChecked).apply()
            BookRepository.isShadowConvertEnabled = isChecked
            val toastMsg = if (isChecked) I18n.t("toast_shadow_enabled") else I18n.t("toast_shadow_disabled")
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
        }

        swService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                OpdsServer.start()
                tvStatus.text = I18n.t("status_running")
                tvStatus.setTextColor(0xFF4CAF50.toInt())
                val ip = getLocalIpAddress() ?: I18n.t("ip_unknown")
                tvIp.text = I18n.t("ip_opds_address", ip)
                logToConsole(I18n.t("log_service_started"))
            } else {
                OpdsServer.stop()
                tvStatus.text = I18n.t("status_stopped")
                tvStatus.setTextColor(0xFFFF9800.toInt())
                tvIp.text = I18n.t("ip_koreader_disabled")
                logToConsole(I18n.t("log_service_stopped"))
            }
        }

        btnMatchMeta.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            SearchSource.values().forEachIndexed { index, source ->
                popup.menu.add(0, index, index, I18n.t("source_${source.name.lowercase()}"))
            }
            
            popup.setOnMenuItemClickListener { item ->
                val selectedSource = SearchSource.values()[item.itemId]
                startDeepSanitize(selectedSource)
                true
            }
            popup.show()
        }

        btnBatchConvert.setOnClickListener {
            val txtFiles = BookRepository.getBooks().filter { it.first.extension.lowercase() == "txt" }
            lifecycleScope.launch(Dispatchers.IO) {
                var count = 0
                txtFiles.forEach { (file, _) ->
                    try {
                        EpubPackager.convertTxtToPhysicalEpub(file)
                        count++
                    } catch (_: Exception) {}
                }
                BookRepository.refresh()
                withContext(Dispatchers.Main) {
                    refreshBookListUI()
                    Toast.makeText(this@MainActivity, I18n.t("toast_convert_success", count), Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnExtractTxt.setOnClickListener {
            val epubFiles = BookRepository.getBooks().filter { it.first.extension.lowercase() == "epub" }
            lifecycleScope.launch(Dispatchers.IO) {
                var count = 0
                epubFiles.forEach { (file, _) ->
                    try {
                        if (EpubExtractor.extractEpubToPhysicalTxt(file) != null) count++
                    } catch (_: Exception) {}
                }
                BookRepository.refresh()
                withContext(Dispatchers.Main) {
                    refreshBookListUI()
                    Toast.makeText(this@MainActivity, I18n.t("toast_extract_success", count), Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<View>(R.id.fab_refresh).setOnClickListener { doRefreshAction() }
        btnHelp.setOnClickListener { showHelpDialog() }
    }

    private fun startDeepSanitize(source: SearchSource) {
        val sourceLabel = I18n.t("source_${source.name.lowercase()}")
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle(I18n.t("dialog_sync_title", sourceLabel))
            .setMessage(I18n.t("dialog_sync_scanning"))
            .setCancelable(false)
            .create()
        
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            val books = BookRepository.getBooks()
            val total = books.size
            var renameCount = 0

            books.forEachIndexed { index, (file, _) ->
                withContext(Dispatchers.Main) {
                    progressDialog.setMessage(I18n.t("dialog_sync_progress", index + 1, total, file.name))
                    logToConsole(I18n.t("log_sync_matching", sourceLabel, file.name))
                }

                try {
                    val result = BookMetadataService.getIntegratedMetadata(file, source) { status ->
                        runOnUiThread { logToConsole(" -> $status") }
                    }

                    val newName = MetadataManager.sanitizePhysicalFile(file, result)
                    if (newName != null) {
                        renameCount++
                        withContext(Dispatchers.Main) {
                            logToConsole(I18n.t("log_sync_rename_success", newName))
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        logToConsole(I18n.t("log_sync_match_fail", file.name))
                    }
                }
            }

            BookRepository.refresh()
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                refreshBookListUI()
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(I18n.t("dialog_sync_done_title"))
                    .setMessage(I18n.t("dialog_sync_done_msg", sourceLabel, total, renameCount))
                    .setPositiveButton(I18n.t("btn_ok"), null)
                    .show()
            }
        }
    }

    private fun doRefreshAction() {
        lifecycleScope.launch(Dispatchers.IO) {
            BookRepository.refresh()
            withContext(Dispatchers.Main) { refreshBookListUI() }
        }
    }

    private fun logToConsole(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        tvConsole.append("[$time] $msg\n")
        scrollConsole.post { scrollConsole.fullScroll(View.FOCUS_DOWN) }
    }

    private fun refreshBookListUI() {
        bookAdapter.submitList(BookRepository.getBooks())
    }

    private fun resolveUriToPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            if ("primary".equals(split[0], true)) {
                "${Environment.getExternalStorageDirectory()}/${split[1]}".trimEnd('/')
            } else null
        } catch (e: Exception) { null }
    }

    private fun getLocalIpAddress(): String? {
        return NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(".") == true }
            ?.hostAddress
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(I18n.t("dialog_perm_title"))
                    .setMessage(I18n.t("dialog_perm_msg"))
                    .setCancelable(false)
                    .setPositiveButton(I18n.t("btn_grant")) { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton(I18n.t("btn_exit")) { _, _ -> finish() }
                    .show()
            }
        } else {
            requestPermissions(arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), 100)
        }
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(I18n.t("dialog_help_title"))
            .setMessage(I18n.t("dialog_help_msg"))
            .setPositiveButton(I18n.t("btn_i_know"), null)
            .show()
    }
}