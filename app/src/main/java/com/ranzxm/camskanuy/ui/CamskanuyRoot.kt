package com.ranzxm.camskanuy.ui

import android.content.ActivityNotFoundException
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.ranzxm.camskanuy.CamskanuyApp
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.data.DocumentWithPages
import com.ranzxm.camskanuy.data.PageEntity
import com.ranzxm.camskanuy.ui.capture.CaptureScreen
import com.ranzxm.camskanuy.ui.capture.CaptureViewModel
import com.ranzxm.camskanuy.ui.common.DocumentUiViewModel
import com.ranzxm.camskanuy.ui.detail.DetailScreen
import com.ranzxm.camskanuy.ui.documents.DocumentsScreen
import com.ranzxm.camskanuy.ui.editor.EditorScreen
import com.ranzxm.camskanuy.ui.editor.EditorViewModel
import com.ranzxm.camskanuy.ui.pages.PagesScreen
import java.io.File
import org.json.JSONArray

@Composable
fun CamskanuyRoot(app: CamskanuyApp) {
    val preferences = remember { app.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var appearance by rememberSaveable { mutableStateOf(preferences.getString("mode", "system") ?: "system") }
    val dark = when (appearance) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFAFC6FF), onPrimary = Color(0xFF102B62),
        primaryContainer = Color(0xFF243E68), onPrimaryContainer = Color(0xFFDCE8FF),
        background = Color(0xFF10151F), surface = Color(0xFF151C28),
        surfaceContainer = Color(0xFF202A39), onSurface = Color(0xFFF0F3F9),
        onSurfaceVariant = Color(0xFFBAC4D4), outlineVariant = Color(0xFF344156),
    ) else lightColorScheme(
        primary = Color(0xFF315DD7), onPrimary = Color.White,
        primaryContainer = Color(0xFFE8EEFF), onPrimaryContainer = Color(0xFF23428D),
        background = Color(0xFFF6F7FB), surface = Color(0xFFFCFCFF),
        surfaceContainer = Color.White, onSurface = Color(0xFF1C2940),
        onSurfaceVariant = Color(0xFF65718A), outlineVariant = Color(0xFFE0E5F0),
    )
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
    )) {
        Surface(color = colors.background) {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = "documents") {
                composable("documents") {
                    DocumentRoute(app, nav, "documents", null, appearance) { value ->
                        appearance = value
                        preferences.edit().putString("mode", value).apply()
                    }
                }
                composable("capture/{documentId}?openGallery={openGallery}", arguments = listOf(
                    navArgument("openGallery") { type = NavType.BoolType; defaultValue = false },
                )) {
                    val model: CaptureViewModel = viewModel()
                    CaptureScreen(model, onBack = { nav.popBackStack() }, onEditor = { source, uris ->
                        val queue = JSONArray(uris.orEmpty()).toString()
                        nav.navigate("editor/${model.documentId}?sourcePath=${Uri.encode(source.orEmpty())}&uris=${Uri.encode(queue)}")
                    }, onPages = { nav.pages(model.documentId) })
                }
                composable("editor/{documentId}?sourcePath={sourcePath}&uris={uris}&pageId={pageId}", arguments = listOf(
                    navArgument("sourcePath") { defaultValue = "" },
                    navArgument("uris") { defaultValue = "[]" },
                    navArgument("pageId") { defaultValue = "" },
                )) {
                    val model: EditorViewModel = viewModel(factory = viewModelFactory {
                        initializer {
                            val saved = createSavedStateHandle()
                            if (saved.get<String>("pageId").isNullOrEmpty()) saved.remove<String>("pageId")
                            if (saved.get<String>("sourcePath").isNullOrEmpty()) saved.remove<String>("sourcePath")
                            val queue = JSONArray(saved.get<String>("uris") ?: "[]")
                            saved["inputUris"] = ArrayList((0 until queue.length()).map { queue.getString(it) })
                            EditorViewModel(app, saved)
                        }
                    })
                    EditorScreen(model, onBack = { nav.popBackStack() }, onPages = { nav.pages(model.documentId) })
                }
                composable("pages/{documentId}") { entry ->
                    DocumentRoute(app, nav, "pages", entry.arguments?.getString("documentId"), appearance) {}
                }
                composable("detail/{documentId}") { entry ->
                    DocumentRoute(app, nav, "detail", entry.arguments?.getString("documentId"), appearance) {}
                }
            }
        }
    }
}

private fun NavHostController.pages(id: String) = navigate("pages/$id") {
    popUpTo("documents")
    launchSingleTop = true
}

private data class Choice(val label: String, val action: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentRoute(
    app: CamskanuyApp, nav: NavHostController, screen: String, documentId: String?,
    appearance: String, setAppearance: (String) -> Unit,
) {
    val model: DocumentUiViewModel = viewModel(factory = viewModelFactory {
        initializer { DocumentUiViewModel(app.repository, app.exporter, createSavedStateHandle()) }
    })
    val context = LocalContext.current
    val docs by model.documents.collectAsStateWithLifecycle()
    val folders by model.folders.collectAsStateWithLifecycle()
    val event by model.event.collectAsStateWithLifecycle()
    val pending by model.pendingExport.collectAsStateWithLifecycle()
    val failed by model.error.collectAsStateWithLifecycle()
    val working by model.working.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var notice by remember { mutableStateOf<String?>(null) }
    var choices by remember { mutableStateOf<List<Choice>?>(null) }
    var sheetTitle by remember { mutableStateOf("") }
    var nameTitle by remember { mutableStateOf<String?>(null) }
    var nameValue by rememberSaveable { mutableStateOf("") }
    var nameAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var confirmTitle by remember { mutableStateOf<String?>(null) }
    var confirmBody by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val doc = docs?.find { it.document.id == documentId }
    fun label(id: Int) = context.getString(id)
    fun sheet(title: String, items: List<Choice>) { sheetTitle = title; choices = items }
    fun name(title: Int, value: String, action: (String) -> Unit) {
        nameTitle = label(title); nameValue = value; nameAction = action
    }
    fun confirm(title: Int, body: String, action: () -> Unit) {
        confirmTitle = label(title); confirmBody = body; confirmAction = action
    }
    fun delete(record: DocumentWithPages) {
        val draft = record.document.isDraft
        confirm(if (draft) R.string.doc_discard_title else R.string.doc_delete_title,
            if (draft) label(R.string.doc_discard_message) else context.getString(R.string.doc_delete_message, record.document.name)) {
            model.deleteDocument(record.document.id, documentId == record.document.id)
        }
    }
    fun export(record: DocumentWithPages, share: Boolean) {
        val page = record.sortedPages.getOrNull(if (screen == "detail") model.pageIndex.value else 0)
        sheet(label(if (share) R.string.doc_share else R.string.doc_export), listOf(
            Choice(label(R.string.doc_pdf)) { model.prepareExport(record.document.id, page?.id, true, share) },
            Choice(label(R.string.doc_jpg)) { model.prepareExport(record.document.id, page?.id, false, share) },
        ))
    }
    fun actions(record: DocumentWithPages) {
        sheet(record.document.name, buildList {
            if (record.document.isDraft) add(Choice(label(R.string.doc_resume)) { nav.pages(record.document.id) })
            add(Choice(label(R.string.doc_rename)) { name(R.string.doc_name, record.document.name) { model.rename(record.document.id, it) } })
            add(Choice(label(R.string.doc_move)) {
                sheet(label(R.string.doc_move), listOf(Choice(label(R.string.doc_unfiled)) { model.move(record.document.id, null) }) +
                    folders.map { folder -> Choice(folder.name) { model.move(record.document.id, folder.id) } })
            })
            if (record.pages.isNotEmpty()) add(Choice(label(R.string.doc_share)) { export(record, true) })
            add(Choice(label(if (record.document.isDraft) R.string.doc_discard else R.string.doc_delete)) { delete(record) })
        })
    }
    fun back() {
        if (model.busy) return
        if (screen == "pages" && doc?.document?.isDraft == true) {
            sheet(label(R.string.doc_leave_draft), listOf(
                Choice(label(R.string.doc_keep_draft)) { nav.popBackStack("documents", false) },
                Choice(label(R.string.doc_keep_editing)) {},
                Choice(label(R.string.doc_discard)) { delete(doc) },
            ))
        } else nav.popBackStack()
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), model::finishExport)
    val jpgPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg"), model::finishExport)
    LaunchedEffect(event) {
        val value = event ?: return@LaunchedEffect
        model.consumeEvent()
        when (value.getString("kind")) {
            "capture" -> nav.navigate("capture/${value.getString("documentId")}?openGallery=${value.getBoolean("openGallery")}")
            "saved" -> nav.navigate("detail/${value.getString("documentId")}") { popUpTo("documents"); launchSingleTop = true }
            "home" -> nav.popBackStack("documents", false)
            "message" -> notice = context.getString(value.getInt("message", R.string.doc_saved))
        }
    }
    LaunchedEffect(pending) {
        val value = pending ?: return@LaunchedEffect
        if (value.getBoolean("launched")) return@LaunchedEffect
        model.markExportLaunched()
        try {
            if (value.getBoolean("share")) {
                val file = File(requireNotNull(value.getString("path")))
                require(file.isFile && file.length() > 0) { "Shared export is unavailable" }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = value.getString("mime")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, label(R.string.doc_share)))
                model.clearExport()
            } else {
                val filename = value.getString("name").orEmpty()
                if (value.getString("mime") == "application/pdf") pdfPicker.launch(filename) else jpgPicker.launch(filename)
            }
        } catch (_: ActivityNotFoundException) {
            model.clearExport(); notice = label(R.string.doc_no_receiver)
        } catch (_: IllegalArgumentException) {
            model.clearExport(); notice = label(R.string.doc_error)
        } catch (_: SecurityException) {
            model.clearExport(); notice = label(R.string.doc_error)
        }
    }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); notice = null } }
    LaunchedEffect(failed) {
        if (failed && snackbar.showSnackbar(label(R.string.doc_error), label(R.string.doc_retry), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) model.retry()
    }
    if (screen != "documents") BackHandler { back() }
    Box(Modifier.fillMaxSize()) {
        when (screen) {
            "documents" -> DocumentsScreen(model, onOpen = { record ->
                if (record.document.isDraft) nav.pages(record.document.id) else nav.navigate("detail/${record.document.id}")
            }, onActions = ::actions, onFolders = {
                sheet(label(R.string.doc_folders), listOf(
                    Choice(label(R.string.doc_new_folder)) { name(R.string.doc_folder_name, "", model::createFolder) },
                ) + folders.map { folder -> Choice(folder.name) {
                    sheet(folder.name, listOf(
                        Choice(label(R.string.doc_rename)) { name(R.string.doc_folder_name, folder.name) { model.renameFolder(folder.id, it) } },
                        Choice(label(R.string.doc_delete)) { confirm(R.string.doc_delete, context.getString(R.string.doc_delete_folder_message, folder.name)) { model.deleteFolder(folder.id) } },
                    ))
                } })
            }, onAppearance = {
                sheet(label(R.string.doc_appearance), listOf("system" to R.string.doc_system, "light" to R.string.doc_light, "dark" to R.string.doc_dark).map { (value, title) ->
                    Choice(if (value == appearance) context.getString(R.string.doc_selected, label(title)) else label(title)) { setAppearance(value) }
                })
            }, onInfo = { confirmTitle = label(R.string.doc_info); confirmBody = label(R.string.doc_info_body); confirmAction = null })
            "pages" -> PagesScreen(model, documentId.orEmpty(), onBack = ::back,
                onEdit = { nav.navigate("editor/$documentId?pageId=$it") },
                onAdd = { sheet(label(R.string.doc_add_page), listOf(
                    Choice(label(R.string.doc_camera)) { nav.navigate("capture/$documentId?openGallery=false") },
                    Choice(label(R.string.doc_gallery)) { nav.navigate("capture/$documentId?openGallery=true") },
                )) }, onSave = { doc?.let { record -> name(R.string.doc_name, record.document.name) { model.save(record.document.id, it) } } },
                onPageActions = { page: PageEntity ->
                    val pages = doc?.sortedPages.orEmpty()
                    val index = pages.indexOfFirst { it.id == page.id }
                    sheet(context.getString(R.string.doc_page_number, index + 1), buildList {
                        add(Choice(label(R.string.doc_edit)) { nav.navigate("editor/$documentId?pageId=${page.id}") })
                        fun move(target: Int) { val ids = pages.map { it.id }.toMutableList(); ids.add(target, ids.removeAt(index)); model.reorder(documentId.orEmpty(), ids) }
                        if (index > 0) add(Choice(label(R.string.doc_earlier)) { move(index - 1) })
                        if (index >= 0 && index < pages.lastIndex) add(Choice(label(R.string.doc_later)) { move(index + 1) })
                        add(Choice(label(R.string.doc_delete)) { confirm(R.string.doc_delete_page_title, context.getString(R.string.doc_delete_page_message, index + 1)) { model.deletePage(page.id) } })
                    })
                })
            "detail" -> DetailScreen(model, documentId.orEmpty(), onBack = ::back, onActions = ::actions,
                onEdit = { nav.navigate("pages/$documentId") }, onExport = { share -> doc?.let { export(it, share) } })
        }
        SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth().statusBarsPadding())
    }
    choices?.let { items ->
        ModalBottomSheet(onDismissRequest = { choices = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(sheetTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                items.forEach { item ->
                    TextButton(onClick = { choices = null; if (!model.busy) item.action() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(item.label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    if (nameTitle != null) {
        val valid = nameValue.trim().isNotEmpty() && nameValue.trim().length <= 120
        AlertDialog(onDismissRequest = { nameTitle = null }, title = { Text(nameTitle.orEmpty()) }, text = {
            OutlinedTextField(value = nameValue, onValueChange = { nameValue = it }, singleLine = true,
                label = { Text(nameTitle.orEmpty()) }, isError = nameValue.length > 120,
                supportingText = { if (nameValue.length > 120) Text(stringResource(R.string.doc_name_too_long)) })
        }, confirmButton = { TextButton(enabled = valid && nameAction != null, onClick = { nameAction?.invoke(nameValue.trim()); nameTitle = null }) { Text(stringResource(R.string.doc_save)) } },
            dismissButton = { TextButton(onClick = { nameTitle = null }) { Text(stringResource(R.string.doc_cancel)) } })
    }
    if (confirmTitle != null) AlertDialog(onDismissRequest = { confirmTitle = null }, title = { Text(confirmTitle.orEmpty()) },
        text = { Text(confirmBody) }, confirmButton = {
            TextButton(onClick = { confirmAction?.invoke(); confirmTitle = null }) { Text(stringResource(if (confirmAction == null) R.string.doc_close else R.string.doc_delete)) }
        }, dismissButton = { if (confirmAction != null) TextButton(onClick = { confirmTitle = null }) { Text(stringResource(R.string.doc_cancel)) } })
}
