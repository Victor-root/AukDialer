package auk.dialer.vroot.view.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

private val TitleMinFontSize = 16.sp

/**
 * A top bar title kept to one line, whatever the language or the name in it: one too long for the
 * room the actions leave shrinks a little, then ends in an ellipsis.
 */
@Composable
fun AukTopBarTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = FontWeight.Bold
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = LocalContentColor.current, fontWeight = fontWeight ?: style.fontWeight),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = if (style.fontSize.isSpecified && style.fontSize > TitleMinFontSize) {
            TextAutoSize.StepBased(minFontSize = TitleMinFontSize, maxFontSize = style.fontSize)
        } else {
            null
        }
    )
}
