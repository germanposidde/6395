package com.kmp.hook.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kmp.hook.platform.decodeBase64OrNull
import com.kmp.hook.platform.decodeToImageBitmap
import com.kmp.hook.platform.rememberMediaController
import com.kmp.hook.platform.toBase64

/**
 * A group-photo field: shows the current photo with a delete affordance, or an "Add photo"
 * tile that opens a camera/gallery chooser. Emits the photo as a Base64 JPEG string (or null
 * when cleared). Camera permission handling lives entirely inside [rememberMediaController].
 */
@Composable
fun PhotoField(
    photoBase64: String?,
    onPhotoChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showChooser by remember { mutableStateOf(false) }
    var showDenied by remember { mutableStateOf(false) }

    val controller = rememberMediaController(
        onImagePicked = { bytes -> onPhotoChange(bytes.toBase64()) },
        onCameraDenied = { showDenied = true },
    )

    val bitmap = remember(photoBase64) {
        photoBase64?.decodeBase64OrNull()?.let { decodeToImageBitmap(it) }
    }

    Column(modifier.fillMaxWidth()) {
        Text("Photo", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))

        if (bitmap != null) {
            Box(
                Modifier.fillMaxWidth().height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Group photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp)
                        .size(36.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickableNoRipple { onPhotoChange(null) },
                    contentAlignment = Alignment.Center,
                ) {
                    TrashGlyph(Modifier.size(18.dp), tint = Color.White)
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickableNoRipple { showChooser = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Replace", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .clickableNoRipple { showChooser = true },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlusGlyph(Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add a photo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showChooser) {
        AlertDialog(
            onDismissRequest = { showChooser = false },
            title = { Text("Add photo") },
            text = { Text("Take a new photo or choose one from your gallery.") },
            confirmButton = {
                TextButton(onClick = {
                    showChooser = false
                    controller.captureFromCamera()
                }) { Text("Take photo", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        showChooser = false
                        controller.pickFromGallery()
                    }) { Text("Gallery") }
                    TextButton(onClick = { showChooser = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (showDenied) {
        AlertDialog(
            onDismissRequest = { showDenied = false },
            title = { Text("Camera unavailable") },
            text = { Text("The camera can't be used without permission. You can still add a photo from your gallery.") },
            confirmButton = {
                TextButton(onClick = { showDenied = false }) { Text("Close", fontWeight = FontWeight.Bold) }
            },
        )
    }
}
