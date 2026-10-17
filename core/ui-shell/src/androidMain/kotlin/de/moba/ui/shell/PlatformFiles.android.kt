package de.moba.ui.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.FileOutputStream

@Composable
actual fun rememberPlatformFiles(): PlatformFiles {
    val context = LocalContext.current
    // Wartende Aufrufer; nach einer Bildschirmdrehung ist niemand mehr da, dann passiert einfach nichts.
    val pendingSave = remember { arrayOfNulls<Pair<ByteArray, (String?) -> Unit>>(1) }
    val pendingPick = remember { arrayOfNulls<(ByteArray?) -> Unit>(1) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val (bytes, done) = pendingSave[0] ?: return@rememberLauncherForActivityResult
        pendingSave[0] = null
        if (uri == null) return@rememberLauncherForActivityResult done(null)
        val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }.isSuccess
        done(if (ok) uri.lastPathSegment ?: "gespeichert" else null)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val done = pendingPick[0] ?: return@rememberLauncherForActivityResult
        pendingPick[0] = null
        done(uri?.let { runCatching { context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }.getOrNull() })
    }
    return remember(context) {
        object : PlatformFiles {
            override fun save(name: String, bytes: ByteArray, onDone: (String?) -> Unit) {
                pendingSave[0] = bytes to onDone
                saver.launch(name)
            }

            override fun pick(onResult: (ByteArray?) -> Unit) {
                pendingPick[0] = onResult
                picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            }

            override fun print(title: String, image: ImageBitmap): String? = runCatching {
                val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val landscape = image.width > image.height
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape() else PrintAttributes.MediaSize.ISO_A4)
                    .build()
                manager.print(title, BitmapPrintAdapter(context, title, image), attributes)
                null
            }.getOrElse { "Drucken fehlgeschlagen: ${it.message}" }
        }
    }
}

/** Eine Seite mit dem Bild, eingepasst in den bedruckbaren Bereich. */
private class BitmapPrintAdapter(private val context: Context, private val title: String, image: ImageBitmap) : PrintDocumentAdapter() {
    // Hardware-Bitmaps lassen sich nicht in ein PDF zeichnen
    private val bitmap = image.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    private var attributes: PrintAttributes? = null

    override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
        attributes = new
        if (cancel?.isCanceled == true) return callback.onLayoutCancelled()
        callback.onLayoutFinished(PrintDocumentInfo.Builder("$title.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(), true)
    }

    override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancel: CancellationSignal?, callback: WriteResultCallback) {
        val pdf = PrintedPdfDocument(context, attributes ?: PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
        try {
            val page = pdf.startPage(0)
            val area = page.info.contentRect
            val scale = minOf(area.width().toFloat() / bitmap.width, area.height().toFloat() / bitmap.height)
            val w = (bitmap.width * scale).toInt()
            val h = (bitmap.height * scale).toInt()
            page.canvas.drawBitmap(bitmap, null, Rect(area.left, area.top, area.left + w, area.top + h), null)
            pdf.finishPage(page)
            FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        } finally {
            pdf.close()
        }
    }
}
