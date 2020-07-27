package dres.data.serializers

import dres.data.dbo.DAO
import dres.data.model.basics.media.MediaItem
import dres.data.model.competition.*
import dres.data.model.competition.TaskDescription
import dres.utilities.extensions.readUID
import dres.utilities.extensions.writeUID
import org.mapdb.DataInput2
import org.mapdb.DataOutput2

object TaskDescriptionSerializer {

    /**
     * Serializes [TaskDescription]
     */
    fun serialize(out: DataOutput2, value: TaskDescription) {
        out.writeUID(value.id)
        out.writeUTF(value.name)
        out.writeUTF(value.taskGroup.name)
        out.writeUTF(value.taskType.name)
        out.packLong(value.duration)
        out.writeUID(value.mediaCollectionId)
        writeTaskDescriptionTarget(out, value.target)
        writeTaskDescriptionComponents(out, value.components)
    }

    /**
     * Deserializes [TaskDescription]
     */
    fun deserialize(input: DataInput2, taskGroups: List<TaskGroup>, taskTypes: List<TaskType>, mediaItems: DAO<MediaItem>): TaskDescription = TaskDescription(
            input.readUID(),
            input.readUTF(),
            input.readUTF().let { n -> taskGroups.first { it.name == n } },
            input.readUTF().let { n -> taskTypes.first { it.name == n } },
            input.unpackLong(),
            input.readUID(),
            readTaskDescriptionTarget(input, mediaItems),
            readTaskDescriptionComponents(input, mediaItems)
    )

    /**
     * Part of serialization of [TaskDescription]. Writes [TaskDescriptionTarget]
     *
     * @param out [DataOutput2] to write to.
     * @param out [TaskDescriptionTarget] to serialize.
     */
    private fun writeTaskDescriptionTarget(out: DataOutput2, target: TaskDescriptionTarget) {
        out.writeUTF(target.javaClass.name)
        when(target) {
            is TaskDescriptionTarget.JudgementTaskDescriptionTarget -> {}
            is TaskDescriptionTarget.MediaSegmentTarget -> {
                out.writeUID(target.item.id)
                TemporalRangeSerializer.serialize(out, target.temporalRange)
            }
            is TaskDescriptionTarget.MediaItemTarget -> {
                out.writeUID(target.item.id)
            }
        }
    }

    /**
     * Part of serialization of [TaskDescription]. Writes [TaskDescriptionComponent]s
     *
     * @param out [DataOutput2] to write to.
     * @param out [TaskDescriptionComponent]s to serialize.
     */
    private fun writeTaskDescriptionComponents(out: DataOutput2, components: List<TaskDescriptionComponent>){
        out.writeInt(components.size)
        components.forEach {
            out.packLong(it.start ?: -1)
            out.packLong(it.end ?: -1)
            out.writeUTF(it.javaClass.name)
            when(it) {
                is TaskDescriptionComponent.TextTaskDescriptionComponent -> {
                    out.writeUTF(it.text)
                }
                is TaskDescriptionComponent.VideoItemSegmentTaskDescriptionComponent -> {
                    out.writeUID(it.item.id)
                    TemporalRangeSerializer.serialize(out, it.temporalRange)
                }
                is TaskDescriptionComponent.ImageItemTaskDescriptionComponent -> {
                    out.writeUID(it.item.id)
                }
                is TaskDescriptionComponent.ExternalImageTaskDescriptionComponent -> {
                    out.writeUTF(it.imageLocation)
                }
                is TaskDescriptionComponent.ExternalVideoTaskDescriptionComponent -> {
                    out.writeUTF(it.videoLocation)
                }
            }
        }
    }

    /**
     * Part of deserialization of [TaskDescription]. Reads [TaskDescriptionTarget]
     *
     * @param input [DataInput2] to read from.
     * @param mediaItems [DAO] to lookup [MediaItem]s
     *
     * @return Deserialized [TaskDescriptionTarget].
     */
    private fun readTaskDescriptionTarget(input: DataInput2, mediaItems: DAO<MediaItem>) : TaskDescriptionTarget {
        return when(val className = input.readUTF()) {
            TaskDescriptionTarget.JudgementTaskDescriptionTarget::class.java.name -> TaskDescriptionTarget.JudgementTaskDescriptionTarget
            TaskDescriptionTarget.MediaSegmentTarget::class.java.name -> {
                TaskDescriptionTarget.MediaSegmentTarget(mediaItems[input.readUID()]!! as MediaItem.VideoItem, TemporalRangeSerializer.deserialize(input, 0))
            }
            TaskDescriptionTarget.MediaItemTarget::class.java.name -> {
                TaskDescriptionTarget.MediaItemTarget(mediaItems[input.readUID()]!!)
            }
            else -> throw IllegalStateException("Failed to deserialize $className; not implemented.")
        }
    }

    /**
     * Part of deserialization of [TaskDescription]. Reads [TaskDescriptionComponent]s
     *
     * @param out [DataInput2] to read from.
     * @param mediaItems [DAO] to lookup [MediaItem]s
     *
     * @return Deserialized [TaskDescriptionTarget]s.
     */
    private fun readTaskDescriptionComponents(input: DataInput2, mediaItems: DAO<MediaItem>) : List<TaskDescriptionComponent> = (0 until input.unpackInt()).map {
        val size = input.readInt()
        return (0 until size).map {
            val start = input.unpackLong().let { if (it == -1L) null else it  }
            val end = input.unpackLong().let { if (it == -1L) null else it  }
            val className = input.readUTF()
            when(className) {
                TaskDescriptionComponent.TextTaskDescriptionComponent::class.java.name -> {
                    TaskDescriptionComponent.TextTaskDescriptionComponent(input.readUTF(), start, end)
                }
                TaskDescriptionComponent.VideoItemSegmentTaskDescriptionComponent::class.java.name-> {
                    TaskDescriptionComponent.VideoItemSegmentTaskDescriptionComponent(mediaItems[input.readUID()]!! as MediaItem.VideoItem, TemporalRangeSerializer.deserialize(input, 0), start, end)
                }
                TaskDescriptionComponent.ImageItemTaskDescriptionComponent::class.java.name -> {
                    TaskDescriptionComponent.ImageItemTaskDescriptionComponent(mediaItems[input.readUID()]!! as MediaItem.ImageItem, start, end)
                }
                TaskDescriptionComponent.ExternalImageTaskDescriptionComponent::class.java.name -> {
                    TaskDescriptionComponent.ExternalImageTaskDescriptionComponent(input.readUTF(), start, end)
                }
                TaskDescriptionComponent.ExternalVideoTaskDescriptionComponent::class.java.name -> {
                    TaskDescriptionComponent.ExternalVideoTaskDescriptionComponent(input.readUTF(), start, end)
                }
                else -> throw IllegalArgumentException("Failed to deserialize $className; not implemented.")
            }
        }
    }
}