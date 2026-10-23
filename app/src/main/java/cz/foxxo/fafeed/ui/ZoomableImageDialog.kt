package cz.foxxo.fafeed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Fullscreen image viewer: pinch to zoom, drag to pan, double tap to zoom in/out,
 * single tap (or X) to close. Panning is clamped so the image never leaves the screen.
 */
@Composable
fun ZoomableImageDialog(
    url: String,
    contentDescription: String? = null,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var loaded by remember { mutableStateOf(false) }

    fun clamp(value: Offset, forScale: Float): Offset {
        if (forScale <= MIN_SCALE) return Offset.Zero
        val maxX = container.width * (forScale - 1f) / 2f
        val maxY = container.height * (forScale - 1f) / 2f
        return Offset(
            value.x.coerceIn(-maxX, maxX),
            value.y.coerceIn(-maxY, maxY),
        )
    }

    fun reset() {
        scale = MIN_SCALE
        offset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { container = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        if (newScale <= MIN_SCALE) {
                            scale = MIN_SCALE
                            offset = Offset.Zero
                            return@detectTransformGestures
                        }
                        val zoomChange = newScale / scale
                        val center = Offset(container.width / 2f, container.height / 2f)
                        val pivot = centroid - center
                        val anchored = pivot * (1f - zoomChange) + offset * zoomChange + pan
                        scale = newScale
                        offset = clamp(anchored, newScale)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { position ->
                            if (scale > MIN_SCALE + 0.05f) {
                                reset()
                            } else {
                                val center = Offset(container.width / 2f, container.height / 2f)
                                val pivot = position - center
                                scale = DOUBLE_TAP_SCALE
                                offset = clamp(pivot * (1f - DOUBLE_TAP_SCALE), DOUBLE_TAP_SCALE)
                            }
                        },
                        onTap = {
                            if (scale > MIN_SCALE + 0.05f) reset() else onDismiss()
                        },
                    )
                },
        ) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                },
                success = {
                    loaded = true
                    SubcomposeAsyncImageContent()
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Obrázek se nepodařilo načíst", color = Color.White)
                    }
                },
            )

            if (loaded && scale <= MIN_SCALE + 0.05f) {
                Text(
                    "Dvojklikem přiblížíš, tažením posuneš",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Zavřít",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
