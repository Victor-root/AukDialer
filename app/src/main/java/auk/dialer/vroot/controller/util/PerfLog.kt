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

    // A frame at 60 Hz has about 16.7 ms; past this one has visibly been late.
    private const val SLOW_FRAME_MS = 17.0

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

    /** Logs every late frame of [window], with where its time went. */
    fun watchFrames(window: Window) {
        if (!BuildConfig.DEBUG_TOOLS) return
        val thread = HandlerThread("AukPerfFrames").apply { start() }
        window.addOnFrameMetricsAvailableListener({ _, metrics, _ ->
            fun ms(id: Int) = metrics.getMetric(id) / 1_000_000.0
            val total = ms(FrameMetrics.TOTAL_DURATION)
            if (total > SLOW_FRAME_MS) {
                d(
                    "slow frame %.1f ms: input %.1f, animation %.1f, layout %.1f, draw %.1f, sync %.1f, gpu %.1f, delay %.1f".format(
                        total,
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
