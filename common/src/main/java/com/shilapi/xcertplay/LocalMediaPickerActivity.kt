package com.shilapi.xcertplay

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Size
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

/** Explicit in-app contract: OEM file managers must never resolve this request. */
class LocalMediaPicker : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String) =
        Intent(context, LocalMediaPickerActivity::class.java).putExtra("mediaType", input)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

class ImportedMediaProvider : FileProvider()

class LocalMediaPickerActivity : ComponentActivity() {
    internal data class Entry(val name: String, val directory: File? = null, val uri: Uri? = null)
    private val video get() = intent.getStringExtra("mediaType") == "video/*"
    private var directory by mutableStateOf<File?>(null)
    private var entries by mutableStateOf<List<Entry>>(emptyList())
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf("")
    private var loadJob: Job? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        browse(directory)
    }

    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLocale.wrap(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiPlayWindowFullscreen.apply(this)
        directory = savedInstanceState?.getString("directory")?.let(::File)
        NewUiColors.night = HongqiPositionLights.night() ?: resolveAppNightNow()
        setContent {
            NewUiTheme {
                BackHandler { back() }
                Column(Modifier.fillMaxSize().background(NewUiColors.Canvas).padding(24.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = { back() }, enabled = !busy) { Text(stringResource(R.string.local_media_up)) }
                        Text(if (video) stringResource(R.string.local_media_video) else stringResource(R.string.local_media_image),
                            modifier = Modifier.weight(1f).padding(top = 12.dp), style = NewUiTypography.ActionTitle)
                        TextButton(onClick = { browse(null) }, enabled = !busy) { Text(stringResource(R.string.local_media_locations)) }
                        TextButton(onClick = { finish() }) { Text(stringResource(android.R.string.cancel)) }
                    }
                    Text(directory?.absolutePath ?: stringResource(R.string.local_media_locations),
                        color = NewUiColors.TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (message.isNotEmpty()) Text(message, Modifier.padding(vertical = 8.dp), color = NewUiColors.TextSecondary)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 176.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(entries) { entry ->
                            MediaGridItem(this@LocalMediaPickerActivity, entry, video, !busy) {
                                entry.directory?.let { browse(it) } ?: entry.uri?.let { importFile(it, entry.name) }
                            }
                        }
                    }
                }
            }
        }
        permission.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(
            if (video) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES,
        ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("directory", directory?.absolutePath)
        super.onSaveInstanceState(outState)
    }

    private fun back() {
        if (busy) { finish(); return }
        val current = directory
        if (current == null) finish() else {
            val roots = storageRoots()
            browse(if (roots.any { it.canonicalPath == current.canonicalPath }) null else current.parentFile)
        }
    }

    private fun storageRoots(): List<File> = (listOf(Environment.getExternalStorageDirectory()) +
        File("/storage").listFiles().orEmpty().filter { it.isDirectory && it.name != "emulated" && it.name != "self" } +
        getExternalFilesDirs(null).filterNotNull().mapNotNull { it.parentFile?.parentFile?.parentFile?.parentFile })
        .distinctBy { it.canonicalPath }

    private fun browse(target: File?) {
        loadJob?.cancel()
        directory = target
        loadJob = lifecycleScope.launch {
            busy = true; entries = emptyList(); message = ""
            try {
                entries = withContext(Dispatchers.IO) {
                    if (target == null) {
                        storageRoots().map { Entry(it.absolutePath, directory = it) } + indexedMedia()
                    } else {
                        val files = target.listFiles() ?: throw java.io.IOException()
                        files.filter { !it.name.startsWith(".") && (it.isDirectory || acceptsMediaFile(it.name, video)) }
                            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                            .map { if (it.isDirectory) Entry(it.name, directory = it) else Entry(it.name, uri = Uri.fromFile(it)) }
                    }
                }
                if (entries.isEmpty()) message = getString(R.string.local_media_empty)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = getString(R.string.local_media_unreadable) }
            finally { busy = false }
        }
    }

    private fun indexedMedia(): List<Entry> {
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return try {
            contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(Entry(cursor.getString(1) ?: "—", uri = ContentUris.withAppendedId(collection, cursor.getLong(0))))
                }
            }.orEmpty()
        } catch (_: SecurityException) { emptyList() }
    }

    private fun importFile(uri: Uri, name: String) {
        lifecycleScope.launch {
            busy = true; message = getString(R.string.local_media_importing)
            var output: File? = null
            try {
                val file = withContext(Dispatchers.IO) {
                    val folder = File(filesDir, "imported-media").apply { mkdirs() }
                    val extension = name.substringAfterLast('.', "").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                        ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri)).orEmpty()
                    File(folder, "${UUID.randomUUID()}.$extension").also { dest ->
                        output = dest
                        requireNotNull(contentResolver.openInputStream(uri)).use { input ->
                            dest.outputStream().use { stream ->
                                val buffer = ByteArray(128 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    stream.write(buffer, 0, count)
                                }
                            }
                        }
                        require(dest.length() > 0)
                    }
                }
                val result = FileProvider.getUriForFile(this@LocalMediaPickerActivity, "$packageName.imported-media", file)
                setResult(RESULT_OK, Intent().setData(result))
                finish()
            } catch (e: CancellationException) { output?.delete(); throw e }
            catch (_: Exception) { output?.delete(); message = getString(R.string.local_media_unreadable) }
            finally { busy = false }
        }
    }
}

@Composable
private fun MediaGridItem(context: Context, entry: LocalMediaPickerActivity.Entry, video: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(NewUiColors.CardNormal)
            .border(1.dp, NewUiColors.CardBorder.copy(alpha = 0.55f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.28f).clip(RoundedCornerShape(8.dp))
                .background(NewUiColors.CardSecondary),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.directory != null) {
                Text("▰", color = NewUiColors.Accent, fontSize = 42.sp)
            } else {
                val thumbnail by produceState<Bitmap?>(null, entry.uri, video) {
                    value = entry.uri?.let { uri ->
                        withContext(Dispatchers.IO) { loadMediaThumbnail(context, uri, video) }
                    }
                }
                if (thumbnail != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnail!!.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(if (video) "▶" else "▧", color = NewUiColors.TextSecondary, fontSize = 34.sp)
                }
                if (video) {
                    Text("▶", color = Color.White, fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .background(Color(0x99000000), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        Text(
            entry.name, color = NewUiColors.TextPrimary, style = NewUiTypography.SettingTitle,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(horizontal = 4.dp, vertical = 9.dp),
        )
    }
}

private fun loadMediaThumbnail(context: Context, uri: Uri, video: Boolean): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 29) {
        context.contentResolver.loadThumbnail(uri, Size(640, 480), null)
    } else if (video && uri.scheme == "file") {
        @Suppress("DEPRECATION")
        android.media.ThumbnailUtils.createVideoThumbnail(uri.path!!, MediaStore.Video.Thumbnails.MINI_KIND)
    } else {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            input.mark(Int.MAX_VALUE)
            BitmapFactory.decodeStream(input, null, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sample = maxOf(1, minOf(bounds.outWidth / 640, bounds.outHeight / 480))
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }
} catch (_: Exception) { null }

internal fun acceptsMediaFile(name: String, video: Boolean): Boolean {
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
    return mime?.startsWith(if (video) "video/" else "image/") == true
}
