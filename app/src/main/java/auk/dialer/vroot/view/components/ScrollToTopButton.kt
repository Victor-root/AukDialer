package auk.dialer.vroot.view.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.view.theme.AukMotion

private val ScrollToTopSize = 56.dp

@Composable
fun ScrollToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                animationSpec = AukMotion.spatialDefault(),
                initialScale = 0.7f
            ) + fadeIn(animationSpec = AukMotion.effectsDefault()),
            exit = scaleOut(
                animationSpec = AukMotion.spatialFast(),
                targetScale = 0.7f
            ) + fadeOut(animationSpec = AukMotion.effectsFast()),
            modifier = modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            FilledTonalIconButton(
                onClick = onClick,
                shapes = IconButtonDefaults.shapes(
                    shape = MaterialTheme.shapes.extraLarge,
                    pressedShape = MaterialTheme.shapes.medium
                ),
                modifier = Modifier.size(ScrollToTopSize),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.content_desc_scroll_to_top)
                )
            }
        }
    }
}
