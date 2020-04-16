package dres.data.model.basics.media

import java.time.Duration


/**
 * Interface for [MediaItem]s that allow for playback and thus have a notion of duration and frames.
 *
 * @author Ralph Gasser
 * @version 1.0
 */
interface PlayableMediaItem {
    /** Duration of the [PlayableMediaItem] in milliseconds. */
    val durationMs: Long

    /** [Duration] of the [PlayableMediaItem]. */
    val duration: Duration
        get() = Duration.ofMillis(this.durationMs)

    /** Frame per second for this [PlayableMediaItem]. */
    val fps: Float
}