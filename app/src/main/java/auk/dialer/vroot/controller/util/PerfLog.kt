package auk.dialer.vroot.controller.util

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import auk.dialer.vroot.BuildConfig

/** Debug builds only: what slows the screen down, logged under [TAG] for reading in Logcat. */
object PerfLog {
    const val TAG = "AukPerf"

    fun d(message: String) {
        if (BuildConfig.DEBUG_TOOLS) Log.d(TAG, message)
    }

    /** Runs [block], logging how long it took. */
    inline fun <T> timed(label: String, block: () -> T): T {
        if (!BuildConfig.DEBUG_TOOLS) return block()
        val startedAt = SystemClock.elapsedRealtime()
        val result = block()
        d("$label in ${SystemClock.elapsedRealtime() - startedAt} ms")
        return result
    }

    /** Logs every frame of [window] that missed its deadline, with where its time went. */
    fun watchFrames(window: Window) {
        if (!BuildConfig.DEBUG_TOOLS) return
        val thread = HandlerThread("AukPerfFrames").apply { start() }
        window.addOnFrameMetricsAvailableListener({ _, metrics, _ ->
            fun ms(id: Int) = metrics.getMetric(id) / 1_000_000.0
            val total = ms(FrameMetrics.TOTAL_DURATION)
            // The deadline the system gave the frame, which follows the screen's refresh rate.
            val deadline = ms(FrameMetrics.DEADLINE)
            if (total > deadline + 1) {
                d(
                    "late frame %.1f ms for %.1f: input %.1f, animation %.1f, layout %.1f, draw %.1f, sync %.1f, gpu %.1f, delay %.1f".format(
                        total,
                        deadline,
                        ms(FrameMetrics.INPUT_HANDLING_DURATION),
                        ms(FrameMetrics.ANIMATION_DURATION),
                        ms(FrameMetrics.LAYOUT_MEASURE_DURATION),
                        ms(FrameMetrics.DRAW_DURATION),
                        ms(FrameMetrics.SYNC_DURATION),
                        ms(FrameMetrics.GPU_DURATION),
                        ms(FrameMetrics.UNKNOWN_DELAY_DURATION)
                    )
                )
            }
        }, Handler(thread.looper))
    }
}
