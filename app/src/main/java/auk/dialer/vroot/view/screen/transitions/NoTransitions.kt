package auk.dialer.vroot.view.screen.transitions

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.navigation.NavBackStackEntry
import com.ramcosta.composedestinations.spec.DestinationStyle

object NoTransitions : DestinationStyle.Animated() {
    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        EnterTransition.None
    }

    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        ExitTransition.None
    }

    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        EnterTransition.None
    }

    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        ExitTransition.None
    }
}

object SlideUpTransitions : DestinationStyle.Animated() {
    private const val DURATION = 500

    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        slideInVertically(initialOffsetY = { it }, animationSpec = tween(DURATION))
    }

    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        slideOutVertically(targetOffsetY = { -it / 10 }, animationSpec = tween(DURATION)) +
            fadeOut(animationSpec = tween(DURATION), targetAlpha = 0.4f)
    }

    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        slideInVertically(initialOffsetY = { -it / 10 }, animationSpec = tween(DURATION)) +
            fadeIn(animationSpec = tween(DURATION), initialAlpha = 0.4f)
    }

    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        slideOutVertically(targetOffsetY = { it }, animationSpec = tween(DURATION))
    }
}