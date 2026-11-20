package auk.dialer.vroot.view.components

import androidx.compose.runtime.staticCompositionLocalOf
import auk.dialer.vroot.controller.util.PrivacyMask

/** What the screens show of people: see [PrivacyMask]. Provided by the theme. */
val LocalPrivacyMask = staticCompositionLocalOf { PrivacyMask.Off }
