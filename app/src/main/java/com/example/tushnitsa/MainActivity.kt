package com.example.tushnitsa

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var listViewScripts: ListView
    private lateinit var fabAdd: Button

    private val scriptsDir: File by lazy {
        File(filesDir, "scripts").apply { if (!exists()) mkdirs() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listViewScripts = findViewById(R.id.listViewScripts)
        fabAdd = findViewById(R.id.fabAdd)

        checkOverlayPermission()
        loadScriptList()

        // Нажатие на + снизу справа -> открытие окна создания сценария
        fabAdd.setOnClickListener {
            showScriptEditorDialog(null)
        }
    }

    override fun onResume() {
        super.onResume()
        loadScriptList()
    }

    private fun loadScriptList() {
        val files = scriptsDir.listFiles()?.sortedBy { it.name } ?: emptyList()

        val adapter = object : ArrayAdapter<File>(this, R.layout.item_script, files) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_script, parent, false)
                val file = getItem(position) ?: return view

                val tvTitle = view.findViewById<TextView>(R.id.tvScriptTitle)
                val btnPlay = view.findViewById<Button>(R.id.btnPlayItem)

                tvTitle.text = file.nameWithoutExtension

                // Клик по названию -> Редактирование
                tvTitle.setOnClickListener {
                    showScriptEditorDialog(file)
                }

                // Клик по кнопке Play справа -> Запуск суфлера
                btnPlay.setOnClickListener {
                    val text = file.readText()
                    if (text.isNotBlank()) {
                        startOverlayService(text)
                    } else {
                        Toast.makeText(context, "Сценарий пуст!", Toast.LENGTH_SHORT).show()
                    }
                }

                return view
            }
        }
        listViewScripts.adapter = adapter
    }

    private fun showScriptEditorDialog(fileToEdit: File?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_script, null)
        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etTitle = dialogView.findViewById<EditText>(R.id.etDialogTitle)
        val etContent = dialogView.findViewById<EditText>(R.id.etDialogContent)
        val btnSave = dialogView.findViewById<Button>(R.id.btnDialogSave)
        val btnPlay = dialogView.findViewById<Button>(R.id.btnDialogPlay)

        if (fileToEdit != null) {
            tvDialogTitle.text = "Редактирование"
            etTitle.setText(fileToEdit.nameWithoutExtension)
            etContent.setText(fileToEdit.readText())
        } else {
            tvDialogTitle.text = "Новый сценарий"
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val content = etContent.text.toString()

            if (title.isEmpty()) {
                Toast.makeText(this, "Введите название сценария", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Если редактировали имеющийся и сменили название — удаляем старый файл
            if (fileToEdit != null && fileToEdit.nameWithoutExtension != title) {
                fileToEdit.delete()
            }

            val file = File(scriptsDir, "$title.txt")
            file.writeText(content)
            Toast.makeText(this, "Сохранено!", Toast.LENGTH_SHORT).show()
            loadScriptList()
            dialog.dismiss()
        }

        btnPlay.setOnClickListener {
            val text = etContent.text.toString()
            if (text.isNotBlank()) {
                startOverlayService(text)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Сценарий пуст!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun startOverlayService(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Дайте разрешение на показ поверх окон", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("SCRIPT_TEXT", text)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}