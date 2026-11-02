package com.ranit.thingcount.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun CounterIconView(
    icon: String,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val isImageUri = icon.startsWith("http://", ignoreCase = true) ||
            icon.startsWith("https://", ignoreCase = true) ||
            icon.startsWith("content://", ignoreCase = true) ||
            icon.startsWith("file://", ignoreCase = true) ||
            icon.startsWith("/")

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (icon.isNotBlank()) {
            if (isImageUri) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(icon)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = icon,
                    fontSize = (size.value * 0.85).sp
                )
            }
        }
    }
}
