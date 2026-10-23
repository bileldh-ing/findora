package com.findora.app.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Id of the user looking at the screen (null when signed out), provided at the root so listing cards and the detail
 * page can say "Listed by you" on the viewer's own listings without every screen threading the session through.
 */
val LocalViewerId = staticCompositionLocalOf<String?> { null }
