package dev.dres.data.model.basics.media

import dev.dres.data.model.Entity
import dev.dres.data.model.UID

/**
 * A media item such as a video or an image
 *
 * @author Ralph Gasser
 * @version 1.0
 */
sealed class MediaItem : Entity {

    abstract val collection: UID
    abstract val name: String
    abstract val location: String

    abstract fun withCollection(collection: UID): MediaItem

    data class ImageItem constructor(
            override var id: UID,
            override val name: String,
            override val location: String,
            override val collection: UID): MediaItem() {
        override fun withCollection(collection: UID): ImageItem = ImageItem(id, name, location, collection)

        companion object {
            val EMPTY = ImageItem(UID.EMPTY, "n/a", "", UID.EMPTY)
        }
    }

    data class VideoItem constructor(
           override var id: UID,
            override val name: String,
            override val location: String,
            override val collection: UID,
            override val durationMs: Long,
            override val fps: Float
    ): MediaItem(), PlayableMediaItem {
        override fun withCollection(collection: UID): VideoItem = VideoItem(id, name, location, collection, durationMs, fps)

        companion object {
            val EMPTY = VideoItem(UID.EMPTY, "n/a", "", UID.EMPTY, 0, 0f)
        }

    }
}