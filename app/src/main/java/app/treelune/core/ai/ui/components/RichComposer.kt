package app.treelune.core.ai.ui.components

import app.treelune.core.ai.data.RichMessage
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.Saver
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import app.treelune.core.ai.data.*
import app.treelune.core.ai.enrichments.EnrichmentProcessor
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import app.treelune.core.ui.selectors.PointerSelector
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * The blocks being composed, ids included. Rebuilding them from the segments on recreation
 * would give them new ids, and the active block and the open dialog name theirs by id. Empty
 * texts are kept: they are blocks the user made, even if they do not go with the message.
 */
private val ComposerBlocksSaver: Saver<List<ComposerBlock>, String> = Saver(
    save = { blocks ->
        JSONObject()
            .put("ids", JSONArray(blocks.map { it.id }))
            .put("message", RichMessage(blocks.map { it.segment }).toJson())
            .toString()
    },
    restore = { saved ->
        val json = JSONObject(saved)
        val ids = json.getJSONArray("ids")
        val segments = RichMessage.fromJson(json.getString("message"))?.segments
            ?: throw IllegalStateException("Saved composer blocks could not be parsed")
        check(segments.size == ids.length()) { "Saved composer blocks: ${ids.length()} ids for ${segments.size} segments" }
        segments.mapIndexed { i, segment -> ComposerBlock(segment, ids.getString(i)) }
    }
)

/** The enrichment dialog left open, so a rotation reopens it on the same block. */
private val NullableEnrichmentDialogStateSaver: Saver<EnrichmentDialogState?, String> = Saver(
    save = { state ->
        state?.let {
            JSONObject()
                .put("type", it.type.name)
                .put("edited_block_id", it.editedBlockId ?: JSONObject.NULL)
                .put("joined", it.joined ?: JSONObject.NULL)
                .toString()
        }
    },
    restore = { saved ->
        val json = JSONObject(saved)
        EnrichmentDialogState(
            type = EnrichmentType.valueOf(json.getString("type")),
            editedBlockId = if (json.isNull("edited_block_id")) null else json.getString("edited_block_id"),
            joined = if (json.isNull("joined")) null else json.optString("joined").takeIf { it.isNotEmpty() }
        )
    }
)

/**
 * RichComposer: the message as a list of typed blocks — texts, pointers, files — in the order
 * they go.
 *
 * - One block is active at a time (the one touched, typed in, or last added).
 * - A new block goes right after the active one and becomes active; a new text takes the focus.
 * - Blocks are reordered by their handle; the active one stays active wherever it goes.
 * - Deleting the active block activates the one before it (the one after when it was the
 *   first); the list is never empty: an empty text replaces the last block deleted.
 * - An empty text does not go with the message.
 * - « Join » keeps with the session a photo, a video or a sound taken now, a file of the phone,
 *   or the files of a tool's field copied; with no session, none can be joined. An image is a
 *   block of its own, which says when the model does not read images (the message is refused
 *   while it holds one then); a sound or a video a block too, read by the AI as text; a text
 *   goes through its dialog (whole or its first lines). The pointer keeps its own button.
 * The rules live in [ComposerBlocks]; this composable shows the blocks and wires the gestures.
 *
 * [boundedBlocks]: the blocks scroll in a third of the screen, under the controls that stay in
 * view (a conversation's composer); false, they take the height they need and the page around
 * scrolls them with the rest (a routine's editor).
 */
@Composable
fun UI.RichComposer(
    segments: List<MessageSegment>,
    onSegmentsChange: (List<MessageSegment>) -> Unit,
    onSend: (RichMessage) -> Unit,
    placeholder: String = "",
    showEnrichmentButtons: Boolean = true,
    showSendButton: Boolean = true,
    enabled: Boolean = true,
    enrichmentTypes: List<EnrichmentType> = EnrichmentType.entries,
    modifier: Modifier = Modifier,
    sessionType: SessionType = SessionType.CHAT,
    sessionId: String? = null,
    boundedBlocks: Boolean = true,
    statusContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A text joined comes through « Join », with the other files: the pointer alone has its button
    val offeredTypes = enrichmentTypes.filter { it != EnrichmentType.FILE }

    /** The file or the image of [block], if it is one, deleted: taken off the composer, it will never go. */
    fun deleteFile(block: ComposerBlock) {
        val (operation, id) = when (val segment = block.segment) {
            is MessageSegment.Image -> "files.delete" to segment.imageId
            is MessageSegment.Media -> "files.delete" to segment.fileId
            is MessageSegment.EnrichmentBlock ->
                if (segment.type == EnrichmentType.FILE) "files.delete" to app.treelune.core.ai.enrichments.FileEnrichment.fromJson(segment.config).fileId
                else return
            is MessageSegment.Text -> return
        }
        scope.launch {
            val result = Coordinator(context).processUserAction(operation, mapOf("id" to id))
            if (!result.isSuccess) LogManager.aiEnrichment("$operation $id failed: ${result.error}", "ERROR")
        }
    }
    val s = remember { Strings.`for`(context = context) }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxBlocksHeight = screenHeight / 3
    val keyboardController = LocalSoftwareKeyboardController.current

    // The blocks, built once from the segments, then managed here
    var blocks by rememberSaveable(stateSaver = ComposerBlocksSaver) {
        mutableStateOf(ComposerBlocks.fromSegments(segments))
    }

    var activeBlockId by rememberSaveable { mutableStateOf(blocks.first().id) }

    // The text block just added, which takes the focus once shown; not kept across a rotation,
    // where the field that had the focus gets it back by itself
    var focusBlockId by remember { mutableStateOf<String?>(null) }

    // Sync from parent only when segments change externally (not from our own updates)
    var lastSyncedSegments by remember { mutableStateOf(segments) }
    // The blocks as this composition sees them, beside the segments it was given: the effect runs
    // a frame later, when a next keystroke may already have changed the blocks, and the parent's
    // echo of the previous one would then pass for a change from outside
    val blocksWithSegments = blocks
    LaunchedEffect(segments) {
        if (segments != lastSyncedSegments && ComposerBlocks.toSegments(blocksWithSegments) != segments) {
            blocks = ComposerBlocks.fromSegments(segments)
            // Rebuilt blocks have new ids: the active one would name a block that is gone,
            // and the next block would find nowhere to go (the composer emptied after a send)
            activeBlockId = blocks.first().id
        }
        lastSyncedSegments = segments
    }

    var showEnrichmentDialog by rememberSaveable(stateSaver = NullableEnrichmentDialogStateSaver) {
        mutableStateOf<EnrichmentDialogState?>(null)
    }

    /** The blocks replaced by [newBlocks], and the parent told of the message they make. */
    fun update(newBlocks: List<ComposerBlock>) {
        blocks = newBlocks
        onSegmentsChange(ComposerBlocks.toSegments(newBlocks))
    }

    // The block just added, brought into view once laid out: a pointer or an image added after
    // the active block would otherwise stay cut at the bottom of the blocks' scroll
    var revealBlockId by remember { mutableStateOf<String?>(null) }

    /** [block] added after the active one, made active, and brought into view. */
    fun add(block: ComposerBlock) {
        update(ComposerBlocks.insertAfter(blocks, activeBlockId, block))
        activeBlockId = block.id
        revealBlockId = block.id
    }

    // Whether the model the message would go to reads images: read once an image is in the
    // composer (null until then, or when nothing says)
    val hasImages = blocks.any { it.segment is MessageSegment.Image }
    var readsImages by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(hasImages) {
        if (hasImages) readsImages = app.treelune.core.ai.providers.ImageInput.sessionModelReadsImages(context, sessionId)
    }

    /** The photo at [uri] prepared and kept with the session, then added as a block. */
    fun attachImage(uri: android.net.Uri, afterwards: () -> Unit = {}) {
        val session = checkNotNull(sessionId) { "an image is joined within a session" }
        scope.launch {
            val result = Coordinator(context).processUserAction("files.attach_image", mapOf("session_id" to session, "uri" to uri.toString()))
            afterwards()
            if (result.isSuccess) {
                add(ComposerBlock(MessageSegment.Image(result.data!!["id"] as String)))
                // An animated image keeps its first frame, which is said
                if (result.data!!["animated"] == true) UI.Toast(s.shared("image_animated_first_frame"), Duration.LONG)
            } else UI.Toast(result.error ?: "", Duration.LONG)
        }
    }

    /** The files [joined] answered added where they go: an image or a medium as a block, a text through its dialog. */
    fun added(joined: List<Map<*, *>>) {
        joined.forEach { file ->
            val id = file["id"] as String
            when (file["kind"]) {
                app.treelune.core.files.FileKind.IMAGE.name -> {
                    add(ComposerBlock(MessageSegment.Image(id)))
                    // An animated image keeps its first frame, which is said
                    if (file["animated"] == true) UI.Toast(s.shared("image_animated_first_frame"), Duration.LONG)
                }
                app.treelune.core.files.FileKind.TEXT.name -> showEnrichmentDialog = EnrichmentDialogState(EnrichmentType.FILE, editedBlockId = null,
                    joined = app.treelune.core.ai.enrichments.FileEnrichment(id, file["name"] as String, (file["line_count"] as Number).toInt()).toJson().toString())
                else -> add(ComposerBlock(MessageSegment.Media(id)))
            }
        }
    }

    /** Files joined to the session by [params] (a file of the phone, or copies of a tool's), then [afterwards]. */
    fun join(params: Map<String, Any?>, afterwards: () -> Unit = {}) {
        val session = checkNotNull(sessionId) { "a file is joined within a session" }
        scope.launch {
            val result = Coordinator(context).processUserAction("files.join", params + ("session_id" to session))
            afterwards()
            if (result.isSuccess) added((result.data?.get("files") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>())
            else UI.Toast(result.error ?: "", Duration.LONG)
        }
    }

    // The camera writes into a file of the app's cache, handed to it by the FileProvider; the
    // file is kept until it is joined, then deleted. Its path survives the app being stopped
    // while the camera is open.
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    fun captured(taken: Boolean, joinIt: (android.net.Uri, () -> Unit) -> Unit) {
        val file = capturePath?.let { java.io.File(it) } ?: return
        capturePath = null
        if (taken) joinIt(android.net.Uri.fromFile(file)) { file.delete() } else file.delete()
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured(it, ::attachImage) }
    val videoCamera = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { taken ->
        captured(taken) { uri, done -> join(mapOf("uri" to uri.toString()), done) }
    }
    val recorder = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { join(mapOf("uri" to it.toString())) }
    }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) join(mapOf("uri" to uri.toString())) }
    var joining by rememberSaveable { mutableStateOf(false) }
    var copying by rememberSaveable { mutableStateOf(false) }

    /** The phone's camera into a file of the cache ending with [extension], by [launch]. */
    fun capture(extension: String, launch: (android.net.Uri) -> Unit) {
        val file = java.io.File(context.cacheDir, "camera/${java.util.UUID.randomUUID()}.$extension").apply { parentFile?.mkdirs() }
        capturePath = file.path
        try {
            launch(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: android.content.ActivityNotFoundException) {
            capturePath = null
            file.delete()
            UI.Toast(s.shared("ai_image_no_camera"), Duration.LONG)
        }
    }

    val blocksScrollState = rememberScrollState()

    // Close-set: each nesting (the composer's card, a block's, its field) keeps its own frame,
    // and the air between them is the least that parts them
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UI.Space.S)
    ) {

        // The status, over the blocks so that it stays in view when the keyboard appears: the
        // height its text takes, no more
        statusContent?.invoke()

        // The blocks: their own scroll in a third of the screen, or their full height
        Column(
            modifier = if (boundedBlocks) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxBlocksHeight)
                    .verticalScroll(blocksScrollState)
            } else Modifier.fillMaxWidth()
        ) {
            UI.ReorderableColumn(
                items = blocks,
                key = { it.id },
                spacing = UI.Space.S,
                onMove = { from, to -> update(ComposerBlocks.move(blocks, from, to)) }
            ) { _, block ->
                val viewRequester = remember { BringIntoViewRequester() }
                LaunchedEffect(revealBlockId) {
                    if (revealBlockId == block.id) {
                        viewRequester.bringIntoView()
                        revealBlockId = null
                    }
                }
                BlockCard(
                    modifier = Modifier.bringIntoViewRequester(viewRequester),
                    isActive = block.id == activeBlockId,
                    onActivate = { activeBlockId = block.id },
                    onDelete = {
                        deleteFile(block)
                        val deletion = ComposerBlocks.delete(blocks, block.id, activeBlockId)
                        update(deletion.blocks)
                        activeBlockId = deletion.activeId
                    },
                    bar = { end -> DragBar(end) }
                ) {
                    when (val segment = block.segment) {
                        is MessageSegment.Text -> TextBlockContent(
                            text = segment.content,
                            placeholder = if (blocks.size == 1 && segment.content.isEmpty()) {
                                placeholder.ifEmpty { s.shared("ai_composer_placeholder") }
                            } else "",
                            takeFocus = block.id == focusBlockId,
                            onFocusTaken = { focusBlockId = null },
                            // A focused field leaving the screen hands its text over once more as it
                            // loses the focus, after its block is gone (the blocks rebuilt from the
                            // parent's message, or deleted): that text has no block to go to, and
                            // the block is no longer one to make active
                            onActivate = { if (blocks.any { it.id == block.id }) activeBlockId = block.id },
                            onTextChange = { newText ->
                                if (blocks.any { it.id == block.id }) {
                                    update(ComposerBlocks.replace(blocks, block.id, MessageSegment.Text(newText)))
                                }
                            }
                        )
                        is MessageSegment.Image -> ImageBlockContent(imageId = segment.imageId, readsImages = readsImages)
                        is MessageSegment.Media -> app.treelune.core.files.FileRow(listOf(segment.fileId))
                        is MessageSegment.EnrichmentBlock -> EnrichmentBlockContent(
                            block = segment,
                            onEdit = {
                                activeBlockId = block.id
                                showEnrichmentDialog = EnrichmentDialogState(type = segment.type, editedBlockId = block.id)
                            }
                        )
                    }
                }
            }
        }

        // Controls row: Add Text + enrichments, neutral, so that Send alone stands out
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Add Text button: a new text block after the active one, which takes the focus
            UI.ActionButton(
                action = ButtonAction.TEXT,
                display = ButtonDisplay.ICON,
                type = ButtonType.DEFAULT,
                size = Size.M,
                onClick = {
                    val newBlock = ComposerBlock(MessageSegment.Text(""))
                    add(newBlock)
                    focusBlockId = newBlock.id
                }
            )

            // Enrichment buttons (if enabled)
            if (showEnrichmentButtons) {
                offeredTypes.forEach { type ->
                    UI.ActionButton(
                        action = getEnrichmentButtonAction(type),
                        display = ButtonDisplay.ICON,
                        type = ButtonType.DEFAULT,
                        size = Size.M,
                        onClick = {
                            showEnrichmentDialog = EnrichmentDialogState(type = type, editedBlockId = null)
                        }
                    )
                }
            }

            // Join: a photo, a video, a sound taken now, a file of the phone, or the files of a
            // tool's field copied, kept with the session; within a session alone
            if (showEnrichmentButtons && sessionId != null) {
                UI.ActionButton(
                    action = ButtonAction.ATTACH,
                    display = ButtonDisplay.ICON,
                    type = ButtonType.DEFAULT,
                    size = Size.M,
                    onClick = { joining = true }
                )
            }

            // Send, at the right end, in the main colour
            if (showSendButton) {
                Spacer(modifier = Modifier.weight(1f))
                UI.ActionButton(
                    action = ButtonAction.SEND,
                    display = ButtonDisplay.ICON,
                    size = Size.M,
                    enabled = enabled,
                    onClick = {
                        // Hide keyboard when sending message
                        keyboardController?.hide()

                        LogManager.aiEnrichment("RichComposer Send button clicked with ${blocks.size} blocks")
                        // A message with images waits for a model that reads them
                        val imageCount = blocks.count { it.segment is MessageSegment.Image }
                        val refusal = app.treelune.core.ai.providers.ImageInput.refusal(imageCount, readsImages) { s.shared(it) }
                        if (refusal != null) UI.Toast(refusal, Duration.LONG)
                        else onSend(RichMessage(ComposerBlocks.toSegments(blocks)))
                    }
                )
            }
        }
    }

    if (joining) JoinMenu(
        onDismiss = { joining = false },
        onPhoto = { joining = false; capture("jpg") { camera.launch(it) } },
        onVideo = { joining = false; capture("mp4") { videoCamera.launch(it) } },
        onRecord = {
            joining = false
            try {
                recorder.launch(android.content.Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION))
            } catch (e: android.content.ActivityNotFoundException) {
                UI.Toast(s.shared("file_no_recorder"), Duration.LONG)
            }
        },
        onDevice = { joining = false; document.launch(app.treelune.core.files.FilePreparation.pickerTypes(app.treelune.core.files.FileKind.entries)) },
        onCopy = { joining = false; copying = true }
    )
    if (copying) app.treelune.core.ui.selectors.EntryFieldPicker(
        title = s.shared("file_copy_from_tool"),
        accepts = { it.type == app.treelune.core.fields.FieldType.FILE },
        onDismiss = { copying = false },
        onPick = { picked ->
            copying = false
            scope.launch {
                val ids = app.treelune.core.files.FileCopies.of(context, picked)
                if (ids == null) UI.Toast(s.shared("error_loading_options"), Duration.LONG)
                else if (ids.isNotEmpty()) join(mapOf("ids" to ids))
            }
        }
    )

    // Enrichment configuration dialog
    showEnrichmentDialog?.let { dialogState ->
        val edited = dialogState.editedBlockId?.let { id ->
            checkNotNull(blocks.firstOrNull { it.id == id }) { "Enrichment dialog on block $id, which the composer no longer holds" }
        }
        EnrichmentConfigDialog(
            type = dialogState.type,
            existingConfig = (edited?.segment as? MessageSegment.EnrichmentBlock)?.config ?: dialogState.joined,
            joined = dialogState.joined != null,
            onDismiss = { showEnrichmentDialog = null },
            onConfirm = { config ->
                val enrichment = MessageSegment.EnrichmentBlock(type = dialogState.type, config = config)
                LogManager.aiEnrichment("Enrichment confirmed: type=${dialogState.type}, edited block=${edited?.id}")
                // An edited block keeps its place; a new one goes after the active block
                if (edited != null) update(ComposerBlocks.replace(blocks, edited.id, enrichment))
                else add(ComposerBlock(enrichment))
                showEnrichmentDialog = null
            },
            sessionType = sessionType,
            sessionId = sessionId
        )
    }
}

/**
 * The enrichment dialog open: the type it configures, and the block it edits — none for a new
 * block, which goes after the active one on confirm.
 */
private data class EnrichmentDialogState(
    val type: EnrichmentType,
    val editedBlockId: String?,
    /** A text just joined, its FileEnrichment as stored, which cancelling deletes */
    val joined: String? = null
)

/**
 * The frame every block shares, whatever its type: a band across its top, the grip it is dragged
 * by with its delete button at the end ([bar], given that button), then its content across the
 * whole width; highlighted when active, activated when touched.
 */
@Composable
private fun BlockCard(
    isActive: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    bar: @Composable (end: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onActivate() }
    ) {
        UI.Card(
            type = CardType.DEFAULT,
            highlight = isActive
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = UI.Space.XS, end = UI.Space.XS, bottom = UI.Space.XS)
            ) {
                bar {
                    UI.ActionButton(
                        action = ButtonAction.DELETE,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = onDelete
                    )
                }
                content()
            }
        }
    }
}

/** A text block's content: its field, which activates the block when focused or typed in. */
@Composable
private fun TextBlockContent(
    text: String,
    placeholder: String,
    takeFocus: Boolean,
    onFocusTaken: () -> Unit,
    onActivate: () -> Unit,
    onTextChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    if (takeFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            onFocusTaken()
        }
    }
    // The card says what the field is for: its placeholder stands inside it alone
    UI.FormField(
        required = false,
        label = placeholder,
        labelAbove = false,
        value = text,
        onChange = { newText ->
            onTextChange(newText)
            onActivate()
        },
        fieldType = FieldType.TEXT_UNLIMITED,
        fieldModifier = FieldModifier(
            focusRequester = focusRequester,
            onFocusChanged = { focusState ->
                if (focusState.isFocused) onActivate()
            }
        )
    )
}

/**
 * An image's content: its thumbnail, and what keeps it from going when the model does not read
 * images or nothing says it does.
 */
@Composable
private fun ImageBlockContent(imageId: String, readsImages: Boolean?) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        app.treelune.core.files.ImageRow(listOf(imageId))
        when (readsImages) {
            true -> {}
            false -> UI.Text(s.shared("ai_image_model_does_not_read"), TextType.WARNING)
            null -> UI.Text(s.shared("ai_image_model_unknown"), TextType.WARNING)
        }
    }
}

/**
 * A pointer's or a file's content: its icon and its text, which stays readable while the edit
 * button keeps its room.
 */
@Composable
private fun EnrichmentBlockContent(
    block: MessageSegment.EnrichmentBlock,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Icon(iconName = block.type.iconName, size = 20.dp)
            UI.Text(
                text = rememberDisplayText(block),
                type = TextType.BODY
            )
        }
        UI.ActionButton(
            action = ButtonAction.EDIT,
            display = ButtonDisplay.ICON,
            size = Size.S,
            onClick = onEdit
        )
    }
}

/**
 * Get button action for enrichment type
 */
private fun getEnrichmentButtonAction(type: EnrichmentType): ButtonAction {
    return when (type) {
        EnrichmentType.POINTER -> ButtonAction.POINTER
        EnrichmentType.FILE -> ButtonAction.ATTACH
    }
}

/**
 * Enrichment configuration dialog with specific UI for each enrichment type
 */
@Composable
private fun EnrichmentConfigDialog(
    type: EnrichmentType,
    existingConfig: String?,
    joined: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (config: String) -> Unit,
    sessionType: SessionType = SessionType.CHAT,
    sessionId: String? = null
) {
    when (type) {
        EnrichmentType.FILE -> FileEnrichmentDialog(
            config = checkNotNull(existingConfig) { "a text's dialog opens on a text joined" },
            joined = joined,
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
        EnrichmentType.POINTER -> PointerEnrichmentDialog(
            existingConfig = existingConfig,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            sessionType = sessionType
        )
    }
}

/**
 * The POINTER enrichment's dialog: the pointer selector, its dates relative to the scheduled time
 * of the run for a starting message that is replayed later (SEED), fixed for a chat.
 */
@Composable
private fun PointerEnrichmentDialog(
    existingConfig: String?,
    onDismiss: () -> Unit,
    onConfirm: (config: String) -> Unit,
    sessionType: SessionType = SessionType.CHAT
) {
    val context = LocalContext.current
    PointerSelector(
        reference = if (sessionType == SessionType.SEED) Strings.`for`(context = context).shared("instant_reference_scheduled_run") else null,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

/** What « Join » offers: a photo, a video, a sound taken now, a file of the phone, the files of a tool's field. */
@Composable
private fun JoinMenu(onDismiss: () -> Unit, onPhoto: () -> Unit, onVideo: () -> Unit, onRecord: () -> Unit, onDevice: () -> Unit, onCopy: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    UI.Dialog(type = DialogType.SELECTION, onConfirm = onDismiss, onCancel = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Text(text = s.shared("join_title"), type = TextType.TITLE, fillMaxWidth = true)
            UI.ActionButton(action = ButtonAction.PHOTO, type = ButtonType.DEFAULT, onClick = onPhoto)
            UI.ActionButton(action = ButtonAction.VIDEO, type = ButtonType.DEFAULT, onClick = onVideo)
            UI.ActionButton(action = ButtonAction.RECORD, type = ButtonType.DEFAULT, onClick = onRecord)
            UI.ActionButton(action = ButtonAction.DEVICE, type = ButtonType.DEFAULT, onClick = onDevice)
            UI.Button(type = ButtonType.DEFAULT, onClick = onCopy) { UI.Text(s.shared("file_copy_from_tool_short"), TextType.LABEL) }
        }
    }
}
