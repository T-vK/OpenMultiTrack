package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    foreground: Color = Color.Black,
    background: Color = Color.White,
) {
    val matrix = remember(content) {
        runCatching {
            val writer = QRCodeWriter()
            writer.encode(content, BarcodeFormat.QR_CODE, 256, 256)
        }.getOrNull()
    }
    Canvas(modifier.size(size)) {
        val bitMatrix = matrix ?: return@Canvas
        val cellW = size.toPx() / bitMatrix.width
        val cellH = size.toPx() / bitMatrix.height
        drawRect(background, size = this.size)
        for (x in 0 until bitMatrix.width) {
            for (y in 0 until bitMatrix.height) {
                if (bitMatrix.get(x, y)) {
                    drawRect(
                        foreground,
                        topLeft = Offset(x * cellW, y * cellH),
                        size = Size(cellW, cellH),
                    )
                }
            }
        }
    }
}
