package dres.api.rest.handler


import dres.api.rest.AccessManager
import dres.api.rest.RestApiRole
import dres.api.rest.types.status.ErrorStatus
import dres.api.rest.types.status.ErrorStatusException
import dres.api.rest.types.status.SuccessStatus
import dres.data.dbo.DAO
import dres.data.dbo.DaoIndexer
import dres.data.model.Config
import dres.data.model.UID
import dres.data.model.basics.media.MediaCollection
import dres.data.model.basics.media.MediaItem
import dres.data.model.basics.media.MediaItemSegmentList
import dres.data.model.basics.media.PlayableMediaItem
import dres.data.model.competition.TaskType
import dres.data.model.run.Submission
import dres.data.model.run.SubmissionStatus
import dres.run.RunManager
import dres.run.RunManagerStatus
import dres.run.audit.AuditLogger
import dres.run.audit.LogEventSource
import dres.run.eventstream.EventStreamProcessor
import dres.run.eventstream.SubmissionEvent
import dres.utilities.FFmpegUtil
import dres.utilities.TimeUtil
import dres.utilities.extensions.sessionId
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.OpenApi
import io.javalin.plugin.openapi.annotations.OpenApiContent
import io.javalin.plugin.openapi.annotations.OpenApiParam
import io.javalin.plugin.openapi.annotations.OpenApiResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.abs

class SubmissionHandler (val collections: DAO<MediaCollection>, private val itemIndex: DaoIndexer<MediaItem, Pair<UID, String>>, private val segmentIndex: DaoIndexer<MediaItemSegmentList, UID>, private val config: Config): GetRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.PARTICIPANT)
    override val route = "submit"

    companion object {
        const val PARAMETER_NAME_COLLECTION = "collection"
        const val PARAMETER_NAME_ITEM = "item"
        const val PARAMETER_NAME_SHOT = "shot"
        const val PARAMETER_NAME_FRAME = "frame"
        const val PARAMETER_NAME_TIMECODE = "timecode"
    }


    private fun getRelevantManagers(userId: UID): Set<RunManager> = AccessManager.getRunManagerForUser(userId)

    private fun getActiveRun(userId: UID, ctx: Context): RunManager {
        val managers = getRelevantManagers(userId).filter { it.status == RunManagerStatus.RUNNING_TASK }
        if (managers.isEmpty()) {
            throw ErrorStatusException(404, "There is currently no eligible competition with an active task.", ctx)
        }

        if (managers.size > 1) {
            throw ErrorStatusException(409, "More than one possible competition found: ${managers.joinToString { it.competitionDescription.name }}", ctx)
        }

        return managers.first()
    }

    private fun toSubmission(ctx: Context, userId: UID, runManager: RunManager, submissionTime: Long): Submission {
        val map = ctx.queryParamMap()
        val team = runManager.competitionDescription.teams.indexOf(runManager.competitionDescription.teams.first { it.users.contains(userId) })

        val collectionParam = map[PARAMETER_NAME_COLLECTION]?.first()
        val collectionId: UID = when {
            collectionParam != null -> this.collections.find { it.name == collectionParam }?.id
            else -> runManager.currentTask?.mediaCollectionId
        } ?: throw ErrorStatusException(404, "Media collection '$collectionParam' could not be found.", ctx)

        /* Find media item. */
        val itemParam = map[PARAMETER_NAME_ITEM]?.first() ?: throw ErrorStatusException(404, "Parameter '$PARAMETER_NAME_ITEM' is missing but required!'", ctx)
        val item = this.itemIndex[collectionId to itemParam].firstOrNull() ?:
            throw ErrorStatusException(404, "Media item '$itemParam (collection = $collectionId)' could not be found.", ctx)

        val mapToSegment = runManager.currentTask?.taskType?.options?.contains(TaskType.Options.MAP_TO_SEGMENT) == true

        return when {
            map.containsKey(PARAMETER_NAME_SHOT) && item is MediaItem.VideoItem -> {
                val time = this.shotToTime(map[PARAMETER_NAME_SHOT]?.first()!!, item, ctx)
                Submission(team, userId, submissionTime, item, time.first, time.second)
            }
            map.containsKey(PARAMETER_NAME_FRAME) && (item is PlayableMediaItem) -> {
                val time = this.frameToTime(map[PARAMETER_NAME_FRAME]?.first()?.toIntOrNull() ?: throw ErrorStatusException(400, "Parameter '$PARAMETER_NAME_FRAME' must be a number.", ctx), item)
                val range = if(mapToSegment && item is MediaItem.VideoItem) timeToSegment(time, item, ctx) else time to time
                Submission(team, userId, submissionTime, item, range.first, range.second)
            }
            map.containsKey(PARAMETER_NAME_TIMECODE) && (item is PlayableMediaItem) -> {
                val time = this.timecodeToTime(map[PARAMETER_NAME_TIMECODE]?.first()!!, item, ctx)
                val range = if(mapToSegment && item is MediaItem.VideoItem) timeToSegment(time, item, ctx) else time to time
                Submission(team, userId, submissionTime, item, range.first, range.second)
            }
            else -> Submission(team, userId, submissionTime, item)
        }.also {
            it.taskRun = runManager.currentTaskRun
        }
    }

    /**
     * Converts a shot number to a timestamp in milliseconds.
     */
    private fun shotToTime(shot: String, item: MediaItem.VideoItem, ctx: Context): Pair<Long,Long> {
        val segmentList = segmentIndex[item.id].firstOrNull() ?: throw ErrorStatusException(400, "Item '${item.name}' not found.", ctx)
        val segment = segmentList.segments.find { it.name == shot } ?: throw ErrorStatusException(400, "Shot '${item.name}.$shot' not found.", ctx)
        return TimeUtil.toMilliseconds(segment.range, item.fps)
    }

    /**
     * Converts a frame number to a timestamp in milliseconds.
     */
    private fun frameToTime(frame: Int, item: PlayableMediaItem): Long {
        return ((frame / item.fps) * 1000.0).toLong()
    }

    /**
     * Converts a timecode to a timestamp in milliseconds.
     */
    private fun timecodeToTime(timecode: String, item: PlayableMediaItem, ctx: Context): Long {
        return TimeUtil.timeCodeToMilliseconds(timecode, item.fps) ?: throw ErrorStatusException(400, "'$timecode' is not a valid time code", ctx)
    }

    private fun timeToSegment(time: Long, item: MediaItem.VideoItem, ctx: Context): Pair<Long,Long> {
        val segmentList = segmentIndex[item.id].firstOrNull() ?: throw ErrorStatusException(400, "Item '${item.name}' not found.", ctx)
        if (segmentList.segments.isEmpty()) {
            throw ErrorStatusException(400, "No segments found for item '${item.name}'.", ctx)
        }
        val segment = segmentList.segments.find {
            val range = TimeUtil.toMilliseconds(it.range, item.fps)
            range.first <= time && range.second >= time
        } ?: segmentList.segments.minBy { abs(it.range.center - time) }!!

        return TimeUtil.toMilliseconds(segment.range, item.fps)
    }

    @OpenApi(summary = "Endpoint to accept submissions",
            path = "/submit",
            queryParams = [
                OpenApiParam(PARAMETER_NAME_COLLECTION, String::class, "Collection identifier. Optional, in which case the default collection for the run will be considered."),
                OpenApiParam(PARAMETER_NAME_ITEM, String::class, "Identifier for the actual media object or media file."),
                OpenApiParam(PARAMETER_NAME_FRAME, Int::class, "Frame number for media with temporal progression (e.g. video)."),
                OpenApiParam(PARAMETER_NAME_SHOT, Int::class, "Shot number for media with temporal progression (e.g. video)."),
                OpenApiParam(PARAMETER_NAME_TIMECODE, String::class, "Timecode for media with temporal progression (e.g. video).")
            ],
            tags = ["Submission"],
            responses = [
                OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
                OpenApiResponse("208", [OpenApiContent(SuccessStatus::class)]),
                OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("409", [OpenApiContent(ErrorStatus::class)])
            ]
    )
    override fun doGet(ctx: Context): SuccessStatus {
        val userId = AccessManager.getUserIdForSession(ctx.sessionId()) ?: throw ErrorStatusException(401, "Authorization required.", ctx)
        val run = getActiveRun(userId, ctx)
        val time = System.currentTimeMillis()
        val submission = toSubmission(ctx, userId, run, time)
        val result = try {
            run.postSubmission(submission)
        } catch (e: IllegalArgumentException) { //is only thrown by submission filter TODO: nicer exception type
            throw ErrorStatusException(208, "Submission rejected", ctx)
        }

        AuditLogger.submission(run.id, run.currentTask?.name ?: "no task", submission, LogEventSource.REST, ctx.sessionId())
        EventStreamProcessor.event(SubmissionEvent(ctx.sessionId(), submission))

        if (run.currentTask?.taskType?.options?.contains(TaskType.Options.HIDDEN_RESULTS) == true) { //pre-generate preview
            generatePreview(submission)
        }


        return when (result) {
            SubmissionStatus.CORRECT -> SuccessStatus("Submission correct!")
            SubmissionStatus.WRONG -> SuccessStatus("Submission incorrect! Try again")
            SubmissionStatus.INDETERMINATE -> SuccessStatus("Submission received. Waiting for verdict!")
            SubmissionStatus.UNDECIDABLE -> SuccessStatus("Submission undecidable. Try again!")
        }
    }

    private fun generatePreview(submission: Submission) {
        if (submission.item !is MediaItem.VideoItem){
            return
        }
        val collection = collections[submission.item.collection] ?: return
        val cacheLocation = Paths.get(config.cachePath + "/previews")
        val cacheDir = cacheLocation.resolve("${submission.item.collection}/${submission.item.name}")
        val imgPath = cacheDir.resolve("${submission.start}.jpg")
        if (Files.exists(imgPath)){
            return
        }
        val mediaItemLocation = Path.of(collection.basePath, submission.item.location)
        FFmpegUtil.extractFrame(mediaItemLocation, submission.start!!, imgPath)

    }
}