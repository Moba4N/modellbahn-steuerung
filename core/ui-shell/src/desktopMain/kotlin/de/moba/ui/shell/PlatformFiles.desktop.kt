package de.moba.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob
import java.io.File
import javax.swing.JFileChooser

@Composable
actual fun rememberPlatformFiles(): PlatformFiles = remember { DesktopFiles }

/** Windows: Dateidialoge, Ordnerauswahl, Explorer und Drucken ueber Java (Druckerdialog, auch "Microsoft Print to PDF"). */
private object DesktopFiles : PlatformFiles {
    override fun save(name: String, bytes: ByteArray, onDone: (String?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Speichern unter", FileDialog.SAVE).apply {
            file = name
            isVisible = true
        }
        val chosen = dialog.files.firstOrNull() ?: return onDone(null)
        chosen.writeBytes(bytes)
        onDone(chosen.path)
    }

    override fun pick(onResult: (ByteArray?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Datei wählen", FileDialog.LOAD).apply {
            setFilenameFilter { _, n -> n.lowercase().endsWith(".zip") }
            file = "*.zip"
            isVisible = true
        }
        onResult(dialog.files.firstOrNull()?.readBytes())
    }

    override val canChooseFolder = true

    override fun chooseFolder(onResult: (String?) -> Unit) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Ordner der Anlage wählen"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        onResult(if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.path else null)
    }

    override val canOpenFolder: Boolean get() = Desktop.isDesktopSupported()

    override fun openFolder(path: String) {
        runCatching { Desktop.getDesktop().open(File(path)) }
    }

    override fun print(title: String, image: ImageBitmap): String? = runCatching {
        val awt = image.toAwtImage()
        val job = PrinterJob.getPrinterJob()
        job.jobName = title
        val page = job.defaultPage().apply { orientation = if (awt.getWidth(null) > awt.getHeight(null)) PageFormat.LANDSCAPE else PageFormat.PORTRAIT }
        job.setPrintable({ g, format, index ->
            if (index > 0) return@setPrintable Printable.NO_SUCH_PAGE
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2.translate(format.imageableX, format.imageableY)
            val scale = minOf(format.imageableWidth / awt.getWidth(null), format.imageableHeight / awt.getHeight(null))
            g2.scale(scale, scale)
            g2.drawImage(awt, 0, 0, null)
            Printable.PAGE_EXISTS
        }, page)
        if (job.printDialog()) job.print()
        null
    }.getOrElse { "Drucken fehlgeschlagen: ${it.message}" }
}
