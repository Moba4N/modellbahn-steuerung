package de.moba.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import de.moba.adapters.catalog.StationCatalog
import de.moba.core.files.FolderLayoutFiles
import de.moba.core.runtime.LicenseInfo
import de.moba.core.station.StationConfig
import de.moba.modules.driving.DrivingModule
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.modules.trackplan.FileTrackPlanStore
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.ui.TrackPlanScreen
import de.moba.ui.shell.FileDialogKind
import de.moba.ui.shell.FileSupport
import de.moba.ui.shell.FileUiState
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Datei-Menue: Anlagen anlegen und wechseln, zuletzt verwendete, Sicherung, Ressourcen, Drucken. */
@OptIn(ExperimentalTestApi::class)
class FileMenuTest {
    private val root: File = Files.createTempDirectory("moba-dateien").toFile()
    private val settings = mutableMapOf<String, String>()
    private val files = FolderLayoutFiles(root, { settings[it] }, { k, v -> settings[k] = v })
    private var plans = TrackPlanRepository(FileTrackPlanStore(File(files.dir, "gleisbilder.txt")))
    private var reloads = 0

    private fun app(): MobaApp = runBlocking {
        MobaApp(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            drivers = listOf(StationCatalog.simulator),
            moduleFactory = { listOf(DrivingModule(), TrackPlanModule(plans)) },
            license = LicenseInfo.of("basis", "pro"),
        ).also { it.connect(StationConfig("simulator")).join() }
    }

    private fun support(app: MobaApp) = FileSupport(files) {
        reloads++
        plans = TrackPlanRepository(FileTrackPlanStore(File(files.dir, "gleisbilder.txt")))
        app.reloadModules()
    }

    private fun MobaApp.plans() = session.value!!.host.service(TrackPlanService::class)!!

    @BeforeTest
    @AfterTest
    fun reset() {
        FileUiState.menuOpen.value = false
        FileUiState.dialog.value = null
    }

    @Test
    fun newLayoutIsCreatedAndRecentOneReopenedAfterAsking() = runComposeUiTest {
        val app = app()
        app.plans().save()
        setContent { MobaShell(app, listOf(DrivingScreen, TrackPlanScreen), initialScreen = TrackPlanScreen.moduleId, files = support(app)) }
        onNodeWithText("Datei").performClick()
        onNodeWithText("Anlage: Meine Anlage").assertExists()
        onNodeWithText("Neue Anlage …").performClick()
        onNode(hasSetTextAction()).performTextInput("Clubanlage")
        onNodeWithText("Anlegen und öffnen").performClick()
        waitForIdle()
        assertEquals("Clubanlage", files.current.value.name)
        assertEquals(1, reloads)

        // Ungespeicherte Aenderung in der neuen Anlage: vor dem Wechsel kommt die Rueckfrage
        waitUntil(timeoutMillis = 5000) { app.session.value?.host?.service(TrackPlanService::class) != null }
        val service = app.plans()
        service.update(service.plans.value.first().copy(name = "Geändert"))
        onNodeWithText("Datei").performClick()
        onNode(hasText("Meine Anlage") and hasClickAction()).performClick()
        onNodeWithText("Ungespeicherte Änderungen").assertExists()
        onNodeWithText("Ohne Speichern weiter").performClick()
        waitForIdle()
        assertEquals(FolderLayoutFiles.DEFAULT_NAME, files.current.value.name)
        assertEquals(2, reloads)
    }

    @Test
    fun backupResourcesAndPrintDialogsShowTheLayout() = runComposeUiTest {
        val app = app()
        app.plans().save()
        File(root, "klaenge").mkdirs()
        File(root, "klaenge/pfiff.wav").writeText("wav")
        setContent { MobaShell(app, listOf(DrivingScreen, TrackPlanScreen), initialScreen = TrackPlanScreen.moduleId, files = support(app)) }

        FileUiState.dialog.value = FileDialogKind.BACKUP
        onNodeWithText("Noch keine Sicherung.").assertExists()
        onNodeWithText("Jetzt sichern").performClick()
        waitForIdle()
        assertEquals(1, files.backups().size)
        onNodeWithText("Wiederherstellen").assertExists()
        onNodeWithText("Schließen").performClick()

        FileUiState.dialog.value = FileDialogKind.RESOURCES
        onNodeWithText("gleisbilder.txt").assertExists()
        onNodeWithText("klaenge/pfiff.wav").assertExists()
        onNodeWithText("Klang (1)").performClick()
        onNodeWithText("gleisbilder.txt").assertDoesNotExist()
        onNodeWithText("Schließen").performClick()

        FileUiState.dialog.value = FileDialogKind.PRINT
        val first = app.plans().plans.value.first().name
        onNodeWithText("Gleisbild „$first“").assertExists()
        onNodeWithText("Drucken …").assertExists()

        FileUiState.dialog.value = FileDialogKind.EXPORT
        onNodeWithText("Gleisbilder, Bahnhöfe und Booster").assertExists()
        onNodeWithText("Exportieren …").assertIsEnabled()
    }
}
