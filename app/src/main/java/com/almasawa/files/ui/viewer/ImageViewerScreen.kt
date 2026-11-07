package com.almasawa.files.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.almasawa.files.R
import com.almasawa.files.util.IMAGE_EXTS
import com.almasawa.files.util.shareFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun decodeSampled(path: String, max: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > max || bounds.outHeight / sample > max) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun readExif(path: String): List<Pair<String, String>> = try {
    val e = ExifInterface(path)
    listOf(
        "Date" to e.getAttribute(ExifInterface.TAG_DATETIME),
        "Make" to e.getAttribute(ExifInterface.TAG_MAKE),
        "Model" to e.getAttribute(ExifInterface.TAG_MODEL),
        "Width" to e.getAttribute(ExifInterface.TAG_IMAGE_WIDTH),
        "Height" to e.getAttribute(ExifInterface.TAG_IMAGE_LENGTH),
        "Exposure" to e.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
        "F-number" to e.getAttribute(ExifInterface.TAG_F_NUMBER),
        "ISO" to e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
        "Focal length" to e.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
    ).mapNotNull { (k, v) -> if (v.isNullOrBlank()) null else k to v }
} catch (e: Exception) {
    emptyList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(path: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val siblings = remember(path) {
        File(path).parentFile?.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTS }
            ?.sortedBy { it.name.lowercase() }
            ?.map { it.absolutePath }
            ?.ifEmpty { null } ?: listOf(path)
    }
    var index by remember { mutableIntStateOf(siblings.indexOf(path).coerceAtLeast(0)) }
    val current = siblings[index.coerceIn(0, siblings.lastIndex)]
    var rotation by remember(current) { mutableFloatStateOf(0f) }
    var scale by remember(current) { mutableFloatStateOf(1f) }
    var offset by remember(current) { mutableStateOf(Offset.Zero) }
    var showInfo by remember { mutableStateOf(false) }

    val bitmap by produceState<ImageBitmap?>(null, current) {
        value = null
        value = withContext(Dispatchers.IO) { decodeSampled(current, 2048)?.asImageBitmap() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = File(current).name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(current) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale <= 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offset.x, translationY = offset.y,
                        rotationZ = rotation,
                    ),
            )
        }
        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = { Text(File(current).name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = {
                IconButton(onClick = { rotation = (rotation + 90f) % 360f }) { Icon(Icons.Default.RotateRight, stringResource(R.string.img_rotate)) }
                IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, stringResource(R.string.img_info)) }
                IconButton(onClick = { shareFiles(ctx, listOf(current)) }) { Icon(Icons.Default.Share, stringResource(R.string.share)) }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
        )
        if (siblings.size > 1) {
            Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp)) {
                IconButton(onClick = { index = (index - 1 + siblings.size) % siblings.size }) {
                    Icon(Icons.Default.ChevronLeft, stringResource(R.string.img_prev), tint = Color.White)
                }
                IconButton(onClick = { index = (index + 1) % siblings.size }) {
                    Icon(Icons.Default.ChevronRight, stringResource(R.string.img_next), tint = Color.White)
                }
            }
        }
    }

    if (showInfo) {
        val exif by produceState<List<Pair<String, String>>?>(null, current) {
            value = withContext(Dispatchers.IO) { readExif(current) }
        }
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(R.string.img_info)) },
            text = {
                val list = exif
                if (list.isNullOrEmpty()) Text(stringResource(R.string.exif_none))
                else androidx.compose.foundation.layout.Column { list.forEach { (k, v) -> Text("$k: $v") } }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}
