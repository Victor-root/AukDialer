package auk.dialer.vroot.view.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * The current day, as the time it started at. Lists grouped under "Today" and "Yesterday" key their
 * grouping on it, so it is worked out again once the date changes, at midnight or when the app comes
 * back after it, instead of still calling yesterday's calls today's.
 */
@Composable
fun rememberCurrentDay(): Long {
    var day by remember { mutableLongStateOf(startOfDay(System.currentTimeMillis())) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val now = System.currentTimeMillis()
                day = startOfDay(now)
                delay(nextDayStart(now) - now)
            }
        }
    }
    return day
}

private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun nextDayStart(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(time)
    add(Calendar.DAY_OF_YEAR, 1)
}.timeInMillis
