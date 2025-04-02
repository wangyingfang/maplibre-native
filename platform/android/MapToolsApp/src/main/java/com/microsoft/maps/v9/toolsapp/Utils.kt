package com.microsoft.maps.v9.toolsapp

import android.content.Context
import android.util.TypedValue
import androidx.annotation.RawRes
import java.io.*

data class Argb(val a: UInt, val r: UInt, val g: UInt, val b: UInt)

object Utils {
    @JvmStatic
    fun readRawResource(context: Context?, @RawRes rawResource: Int): String {
        return if (context != null) {
            val writer: Writer = StringWriter()
            val buffer = CharArray(1024)
            context.resources.openRawResource(rawResource).use { `is` ->
                val reader: Reader = BufferedReader(InputStreamReader(`is`, "UTF-8"))
                var numRead: Int
                while (reader.read(buffer).also { numRead = it } != -1) {
                    writer.write(buffer, 0, numRead)
                }
            }
            writer.toString()
        } else ""
    }

    fun convertDpToPx(context: Context, dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
}
