package com.grinch.rivo4.debug

import androidx.compose.runtime.Composable

/**
 * Release counterpart of the debug voicemail tools: intentionally empty, so the
 * injector and its menu exist only in debug builds.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun VoicemailDebugMenu(onChanged: () -> Unit) = Unit
