package com.example.migrainetracker.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.migrainetracker.data.entity.MigraineRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExportUtils {

    fun exportMigraineRecordsToCSV(
        context: Context,
        records: List<MigraineRecord>
    ): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "migraine_records_$timeStamp.csv"
            val file = File(context.cacheDir, fileName)

            FileOutputStream(file).use { outputStream ->
                // Заголовки CSV
                val headers = listOf(
                    "ID", "Дата", "Время начала", "Время окончания",
                    "Интенсивность (1-10)", "Лекарство", "Тошнота",
                    "Светобоязнь", "Аура", "Заметки"
                )
                outputStream.write(headers.joinToString(",").toByteArray())
                outputStream.write("\n".toByteArray())

                // Данные
                records.forEach { record ->
                    val row = listOf(
                        record.id.toString(),
                        record.date.toString(),
                        record.time.toString(),
                        record.endTime?.toString() ?: "",
                        record.intensity.toString(),
                        record.medicationName ?: "",
                        if (record.nausea) "Да" else "Нет",
                        if (record.photophobia) "Да" else "Нет",
                        if (record.aura) "Да" else "Нет",
                        record.notes?.replace(",", " ") ?: ""
                    )
                    outputStream.write(row.joinToString(",").toByteArray())
                    outputStream.write("\n".toByteArray())
                }
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareFile(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Экспорт записей мигрени"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}