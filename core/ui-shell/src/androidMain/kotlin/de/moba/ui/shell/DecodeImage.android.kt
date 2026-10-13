package de.moba.ui.shell

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Handyfotos werden verkleinert, damit das Wasserzeichen nicht den Speicher fuellt. */
private const val MAX_SIDE = 1600

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}.getOrNull()
