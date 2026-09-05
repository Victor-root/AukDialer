package auk.dialer.vroot.view.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

object AukShapeDefaults {
    const val DefaultRoundness: Int = 28
    val RoundnessOptions: List<Int> = listOf(32, 28, 20, 12, 0)

    const val BaseExtraSmall: Int = 4
    const val BaseSmall: Int = 8
    const val BaseMedium: Int = 12
    const val BaseLarge: Int = 16
    const val BaseLargeIncreased: Int = 20
    const val BaseExtraLarge: Int = 28
    const val BaseExtraLargeIncreased: Int = 32
    const val BaseExtraExtraLarge: Int = 48

    val None: Shape = RectangleShape
    val Full: Shape = CircleShape
}

fun aukRoundnessScale(roundness: Int): Float =
    roundness.coerceIn(1, AukShapeDefaults.BaseExtraExtraLarge) /
        AukShapeDefaults.DefaultRoundness.toFloat()

fun aukCornerDp(baseDp: Int, roundness: Int): Dp =
    (baseDp * aukRoundnessScale(roundness)).coerceAtLeast(1f).dp

fun aukCornerShape(baseDp: Int, roundness: Int): CornerBasedShape =
    RoundedCornerShape(aukCornerDp(baseDp, roundness))

fun aukShapes(roundness: Int = AukShapeDefaults.DefaultRoundness): Shapes {
    val scale = aukRoundnessScale(roundness)
    fun corner(baseDp: Int): CornerBasedShape {
        val cornerDp = (baseDp * scale).coerceAtLeast(1f).dp
        return RoundedCornerShape(cornerDp)
    }
    return Shapes(
        extraSmall = corner(AukShapeDefaults.BaseExtraSmall),
        small = corner(AukShapeDefaults.BaseSmall),
        medium = corner(AukShapeDefaults.BaseMedium),
        large = corner(AukShapeDefaults.BaseLarge),
        extraLarge = corner(AukShapeDefaults.BaseExtraLarge)
    ).copy(
        largeIncreased = corner(AukShapeDefaults.BaseLargeIncreased),
        extraLargeIncreased = corner(AukShapeDefaults.BaseExtraLargeIncreased),
        extraExtraLarge = corner(AukShapeDefaults.BaseExtraExtraLarge)
    )
}

val LocalCardRoundness: ProvidableCompositionLocal<Int> =
    staticCompositionLocalOf { AukShapeDefaults.DefaultRoundness }

object AukMaterialShapes {
    val Circle: RoundedPolygon get() = MaterialShapes.Circle
    val Square: RoundedPolygon get() = MaterialShapes.Square
    val Slanted: RoundedPolygon get() = MaterialShapes.Slanted
    val Arch: RoundedPolygon get() = MaterialShapes.Arch
    val Fan: RoundedPolygon get() = MaterialShapes.Fan
    val Arrow: RoundedPolygon get() = MaterialShapes.Arrow
    val SemiCircle: RoundedPolygon get() = MaterialShapes.SemiCircle
    val Oval: RoundedPolygon get() = MaterialShapes.Oval
    val Pill: RoundedPolygon get() = MaterialShapes.Pill
    val Triangle: RoundedPolygon get() = MaterialShapes.Triangle
    val Diamond: RoundedPolygon get() = MaterialShapes.Diamond
    val ClamShell: RoundedPolygon get() = MaterialShapes.ClamShell
    val Pentagon: RoundedPolygon get() = MaterialShapes.Pentagon
    val Gem: RoundedPolygon get() = MaterialShapes.Gem
    val Sunny: RoundedPolygon get() = MaterialShapes.Sunny
    val VerySunny: RoundedPolygon get() = MaterialShapes.VerySunny
    val Cookie4Sided: RoundedPolygon get() = MaterialShapes.Cookie4Sided
    val Cookie6Sided: RoundedPolygon get() = MaterialShapes.Cookie6Sided
    val Cookie7Sided: RoundedPolygon get() = MaterialShapes.Cookie7Sided
    val Cookie9Sided: RoundedPolygon get() = MaterialShapes.Cookie9Sided
    val Cookie12Sided: RoundedPolygon get() = MaterialShapes.Cookie12Sided
    val Clover4Leaf: RoundedPolygon get() = MaterialShapes.Clover4Leaf
    val Clover8Leaf: RoundedPolygon get() = MaterialShapes.Clover8Leaf
    val Burst: RoundedPolygon get() = MaterialShapes.Burst
    val SoftBurst: RoundedPolygon get() = MaterialShapes.SoftBurst
    val Boom: RoundedPolygon get() = MaterialShapes.Boom
    val SoftBoom: RoundedPolygon get() = MaterialShapes.SoftBoom
    val Flower: RoundedPolygon get() = MaterialShapes.Flower
    val Puffy: RoundedPolygon get() = MaterialShapes.Puffy
    val PuffyDiamond: RoundedPolygon get() = MaterialShapes.PuffyDiamond
    val PixelCircle: RoundedPolygon get() = MaterialShapes.PixelCircle
    val PixelTriangle: RoundedPolygon get() = MaterialShapes.PixelTriangle
    val Ghostish: RoundedPolygon get() = MaterialShapes.Ghostish
    val Heart: RoundedPolygon get() = MaterialShapes.Heart
    val Bun: RoundedPolygon get() = MaterialShapes.Bun

    val AvatarPolygons: List<RoundedPolygon>
        get() = listOf(Circle, Cookie9Sided, Clover4Leaf, Arch, Pill, Gem, Sunny, PixelCircle)

    val IncomingCallPulse: List<RoundedPolygon>
        get() = listOf(Circle, Cookie9Sided, Clover4Leaf, Circle)

    val AvatarMorphStart: RoundedPolygon get() = Circle
    val AvatarMorphEnd: RoundedPolygon get() = Cookie9Sided
}

const val AUK_AVATAR_SHAPE_SQUIRCLE: Int = 0
const val AUK_AVATAR_SHAPE_CIRCLE: Int = 1
const val AUK_AVATAR_SHAPE_SQUARE: Int = 2
const val AUK_AVATAR_SHAPE_COOKIE: Int = 3
const val AUK_AVATAR_SHAPE_CLOVER: Int = 4
const val AUK_AVATAR_SHAPE_ARCH: Int = 5
const val AUK_AVATAR_SHAPE_PILL: Int = 6
const val AUK_AVATAR_SHAPE_GEM: Int = 7
const val AUK_AVATAR_SHAPE_SUNNY: Int = 8
const val AUK_AVATAR_SHAPE_HEART: Int = 9
const val AUK_AVATAR_SHAPE_BURST: Int = 10

@Composable
fun aukAvatarShape(shapeIndex: Int): Shape = when (shapeIndex) {
    AUK_AVATAR_SHAPE_SQUIRCLE -> MaterialTheme.shapes.large
    AUK_AVATAR_SHAPE_CIRCLE -> CircleShape
    AUK_AVATAR_SHAPE_SQUARE -> RoundedCornerShape(8.dp)
    AUK_AVATAR_SHAPE_COOKIE -> MaterialShapes.Cookie9Sided.toShape()
    AUK_AVATAR_SHAPE_CLOVER -> MaterialShapes.Clover4Leaf.toShape()
    AUK_AVATAR_SHAPE_ARCH -> MaterialShapes.Arch.toShape()
    AUK_AVATAR_SHAPE_PILL -> MaterialShapes.Pill.toShape()
    AUK_AVATAR_SHAPE_GEM -> MaterialShapes.Gem.toShape()
    AUK_AVATAR_SHAPE_SUNNY -> MaterialShapes.Sunny.toShape()
    AUK_AVATAR_SHAPE_HEART -> MaterialShapes.Heart.toShape()
    AUK_AVATAR_SHAPE_BURST -> MaterialShapes.SoftBurst.toShape()
    else -> CircleShape
}

@Composable
fun aukPolygonShape(polygon: RoundedPolygon): Shape = polygon.toShape()

class AukMorphShape(
    private val morph: Morph,
    private val progress: () -> Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)
        val p = progress()
        if (p.isNaN()) return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)
        val path = morph.toPath(p.coerceIn(0f, 1f))
        val bounds = path.getBounds()
        if (bounds.width <= 0f || bounds.height <= 0f || bounds.width.isNaN() || bounds.height.isNaN()) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)
        }
        val scaleX = size.width / bounds.width
        val scaleY = size.height / bounds.height
        if (scaleX.isNaN() || scaleY.isNaN() || scaleX.isInfinite() || scaleY.isInfinite()) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)
        }
        val matrix = Matrix()
        matrix.scale(scaleX, scaleY)
        matrix.translate(-bounds.left, -bounds.top)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

fun aukMorph(start: RoundedPolygon, end: RoundedPolygon): Morph = Morph(start, end)

@Composable
fun rememberAukMorph(start: RoundedPolygon, end: RoundedPolygon): Morph =
    remember(start, end) { Morph(start, end) }

@Composable
fun rememberAukMorphShape(
    start: RoundedPolygon,
    end: RoundedPolygon,
    progress: () -> Float
): Shape {
    val morph = rememberAukMorph(start, end)
    val currentProgress = rememberUpdatedState(progress)
    return remember(morph) { AukMorphShape(morph) { currentProgress.value.invoke() } }
}
