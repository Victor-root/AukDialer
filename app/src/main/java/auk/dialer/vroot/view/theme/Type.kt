package auk.dialer.vroot.view.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun aukTextStyle(
    fontSize: Float,
    lineHeight: Float,
    letterSpacing: Float,
    fontWeight: FontWeight
): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = fontWeight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

val AukTypography: Typography = Typography(
    displayLarge = aukTextStyle(57f, 64f, -0.2f, FontWeight.Normal),
    displayMedium = aukTextStyle(45f, 52f, 0f, FontWeight.Normal),
    displaySmall = aukTextStyle(36f, 44f, 0f, FontWeight.Normal),
    headlineLarge = aukTextStyle(32f, 40f, 0f, FontWeight.Normal),
    headlineMedium = aukTextStyle(28f, 36f, 0f, FontWeight.Normal),
    headlineSmall = aukTextStyle(24f, 32f, 0f, FontWeight.Normal),
    titleLarge = aukTextStyle(22f, 28f, 0f, FontWeight.Normal),
    titleMedium = aukTextStyle(16f, 24f, 0.2f, FontWeight.Medium),
    titleSmall = aukTextStyle(14f, 20f, 0.1f, FontWeight.Medium),
    bodyLarge = aukTextStyle(16f, 24f, 0.5f, FontWeight.Normal),
    bodyMedium = aukTextStyle(14f, 20f, 0.2f, FontWeight.Normal),
    bodySmall = aukTextStyle(12f, 16f, 0.4f, FontWeight.Normal),
    labelLarge = aukTextStyle(14f, 20f, 0.1f, FontWeight.Medium),
    labelMedium = aukTextStyle(12f, 16f, 0.5f, FontWeight.Medium),
    labelSmall = aukTextStyle(11f, 16f, 0.5f, FontWeight.Medium),
    displayLargeEmphasized = aukTextStyle(57f, 64f, 0f, FontWeight.Medium),
    displayMediumEmphasized = aukTextStyle(45f, 52f, 0f, FontWeight.Medium),
    displaySmallEmphasized = aukTextStyle(36f, 44f, 0f, FontWeight.Medium),
    headlineLargeEmphasized = aukTextStyle(32f, 40f, 0f, FontWeight.Medium),
    headlineMediumEmphasized = aukTextStyle(28f, 36f, 0f, FontWeight.Medium),
    headlineSmallEmphasized = aukTextStyle(24f, 32f, 0f, FontWeight.Medium),
    titleLargeEmphasized = aukTextStyle(22f, 28f, 0f, FontWeight.Medium),
    titleMediumEmphasized = aukTextStyle(16f, 24f, 0.15f, FontWeight.Bold),
    titleSmallEmphasized = aukTextStyle(14f, 20f, 0.1f, FontWeight.Bold),
    bodyLargeEmphasized = aukTextStyle(16f, 24f, 0.15f, FontWeight.Medium),
    bodyMediumEmphasized = aukTextStyle(14f, 20f, 0.25f, FontWeight.Medium),
    bodySmallEmphasized = aukTextStyle(12f, 16f, 0.4f, FontWeight.Medium),
    labelLargeEmphasized = aukTextStyle(14f, 20f, 0.1f, FontWeight.Bold),
    labelMediumEmphasized = aukTextStyle(12f, 16f, 0.5f, FontWeight.Bold),
    labelSmallEmphasized = aukTextStyle(11f, 16f, 0.5f, FontWeight.Bold)
)
