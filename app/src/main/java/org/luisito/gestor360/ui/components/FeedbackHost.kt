package org.luisito.gestor360.ui.components

import androidx.compose.runtime.compositionLocalOf

val LocalFeedback = compositionLocalOf<FeedbackViewModel> { error("No FeedbackViewModel provided") }
