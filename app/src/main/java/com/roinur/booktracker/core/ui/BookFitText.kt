package com.roinur.booktracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LocalTextStyle

/** Single-line default for fixed-height screens; full text remains available to accessibility. */
@Composable
internal fun BookFitText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = 1,
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(text, modifier, color = color, fontWeight = fontWeight,
        textAlign = textAlign, overflow = overflow, maxLines = maxLines, style = style)
}
