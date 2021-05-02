package com.raywenderlich.placebook.util

import android.content.Context
import java.io.File

//Allow to delete a single file in app's directory
object FileUtils {
    fun deleteFile(context: Context, filename: String) {
        val dir = context.filesDir
        val file = File(dir, filename)
        file.delete()
    }
}
