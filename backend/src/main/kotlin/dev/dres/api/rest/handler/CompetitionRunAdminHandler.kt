package dev.dres.api.rest.handler

import com.sun.net.httpserver.Authenticator
import dev.dres.api.rest.AccessManager
import dev.dres.api.rest.RestApiRole
import dev.dres.api.rest.types.collection.RestMediaItem
import dev.dres.api.rest.types.competition.CompetitionStartMessage
import dev.dres.api.rest.types.competition.RestCompetitionDescription
import dev.dres.api.rest.types.run.*
import dev.dres.api.rest.types.status.ErrorStatus
import dev.dres.api.rest.types.status.ErrorStatusException
import dev.dres.api.rest.types.status.SuccessStatus
import dev.dres.data.dbo.DAO
import dev.dres.data.model.Config
import dev.dres.data.model.UID
import dev.dres.data.model.basics.media.MediaCollection
import dev.dres.data.model.competition.CompetitionDescription
import dev.dres.data.model.run.InteractiveSynchronousCompetition
import dev.dres.data.model.run.RunActionContext.Companion.runActionContext
import dev.dres.data.model.run.RunProperties
import dev.dres.data.model.submissions.SubmissionStatus
import dev.dres.data.model.submissions.aspects.ItemAspect
import dev.dres.data.model.submissions.aspects.TemporalSubmissionAspect
import dev.dres.data.model.submissions.aspects.TextAspect
import dev.dres.mgmt.admin.UserManager
import dev.dres.run.*
import dev.dres.run.audit.AuditLogger
import dev.dres.run.audit.LogEventSource
import dev.dres.utilities.FFmpegUtil
import dev.dres.utilities.extensions.UID
import dev.dres.utilities.extensions.sessionId
import io.javalin.core.security.RouteRole
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.*
import org.slf4j.LoggerFactory
import java.io.File


abstract class AbstractCompetitionRunAdminRestHandler(
    override val permittedRoles: Set<RouteRole> = setOf(
        RestApiRole.ADMIN,
        RestApiRole.PARTICIPANT
    )
) : AccessManagedRestHandler {

    override val apiVersion = "v1"

    /**
     * Parses the run ID out of the [Context] and throws a 404 [ErrorStatusException] if the parameter is missing.
     *
     * @param ctx The [Context] to parse the runId from.
     * @return [UID] representation of the runId.
     */
    fun runId(ctx: Context) = ctx.pathParamMap().getOrElse("runId") {
        throw ErrorStatusException(400, "Parameter 'runId' is missing!'", ctx)
    }.UID()

    /**
     * Obtains the [InteractiveRunManager] for the given [UID].
     *
     * @param runId The [UID] identifying the [InteractiveRunManager].
     * @return [InteractiveRunManager] or null.
     */
    fun getRun(runId: UID): InteractiveRunManager? {
        val run = RunExecutor.managerForId(runId)
        if (run != null && run is InteractiveRunManager) {
            return run
        }
        return null
    }

    /**
     * ensures that only admins are able to modify the state of synchronous runs
     */
    fun synchronousAdminCheck(runId: UID, ctx: Context) {

        if (getRun(runId) is InteractiveAsynchronousRunManager) {
            return
        }

        if (!AccessManager.rolesOfSession(ctx.sessionId()).contains(RestApiRole.ADMIN)) {
            throw ErrorStatusException(403, "Access Denied.", ctx);
        }

    }
}

/**
 * REST handler to create a [InteractiveSynchronousCompetition].
 */
class CreateCompetitionRunAdminHandler(
    private val competitions: DAO<CompetitionDescription>,
    private val collections: DAO<MediaCollection>,
    config: Config
) : AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)),
    PostRestHandler<SuccessStatus> {

    private val cacheLocation = File(config.cachePath + "/tasks")
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private fun competitionById(id: UID, ctx: Context): CompetitionDescription =
        competitions[id] ?: throw ErrorStatusException(
            404,
            "Competition with ID $id not found.'",
            ctx
        )

    override val route = "run/admin/create"

    @OpenApi(
        summary = "Creates a new competition run from an existing competition",
        path = "/api/v1/run/admin/create",
        method = HttpMethod.POST,
        requestBody = OpenApiRequestBody([OpenApiContent(CompetitionStartMessage::class)]),
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {

        val competitionStartMessage = try {
            ctx.bodyAsClass<CompetitionStartMessage>()
        } catch (e: BadRequestResponse) {
            throw ErrorStatusException(400, "Invalid parameters. This is a programmers error!", ctx)
        }

        val competitionToStart =
            this.competitionById(competitionStartMessage.competitionId.UID(), ctx)

        /* ensure that only one synchronous run of a competition is happening at any given time */
        if (competitionStartMessage.type == RunType.SYNCHRONOUS && RunExecutor.managers().any {
                it is InteractiveSynchronousRunManager && it.description == competitionToStart && it.status != RunManagerStatus.TERMINATED
            }
        ) {
            throw ErrorStatusException(
                400,
                "Synchronous run of competition ${competitionToStart.name} already exists",
                ctx
            )
        }

        val segmentTasks = competitionToStart.getAllCachedVideoItems()

        /* check videos */
        segmentTasks.forEach {
            val item = it.item
            val collection = this.collections[item.collection]
                ?: throw ErrorStatusException(400, "collection ${item.collection} not found", ctx)

            val videoFile = File(File(collection.basePath), item.location)

            if (!videoFile.exists()) {
                logger.error("file ${videoFile.absolutePath} not found for item ${item.name}")
                return@forEach
            }

            val outputFile = File(cacheLocation, it.cacheItemName())
            if (!outputFile.exists()) {
                logger.warn("Query video file for item ${it.item} not found, rendering to ${outputFile.absolutePath}")
                FFmpegUtil.prepareMediaSegmentTask(it, collection.basePath, cacheLocation)
            }

        }

        /* Prepare... */
        try {
            val manager = when (competitionStartMessage.type) {
                RunType.ASYNCHRONOUS -> InteractiveAsynchronousRunManager(
                    competitionToStart,
                    competitionStartMessage.name,
                    competitionStartMessage.properties
                )
                RunType.SYNCHRONOUS -> InteractiveSynchronousRunManager(
                    competitionToStart,
                    competitionStartMessage.name,
                    competitionStartMessage.properties
                )
            }

            /**... and schedule RunManager. */
            RunExecutor.schedule(manager)

            return SuccessStatus("Competition '${competitionStartMessage.name}' was started and is running with ID ${manager.id}.")
        } catch (e: IllegalArgumentException) {
            throw ErrorStatusException(
                400,
                e.message ?: "Invalid parameters. This is a programmers error!",
                ctx
            )
        }
    }
}

/**
 * REST handler to start a [InteractiveSynchronousCompetition].
 */
class StartCompetitionRunAdminHandler : AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)),
    PostRestHandler<SuccessStatus> {
    override val route: String = "run/admin/{runId}/start"

    @OpenApi(
        summary = "Starts a competition run.",
        path = "/api/v1/run/admin/{runId}/start",
        method = HttpMethod.POST,
        pathParams = [OpenApiParam("runId", String::class, "Competition Run ID")],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val runId = runId(ctx)

        val run = getRun(runId) ?: throw ErrorStatusException(404, "Run $runId not found", ctx)

        val rac = runActionContext(ctx, run)

        try {
            run.start(rac)
            AuditLogger.competitionStart(run.id, run.description, LogEventSource.REST, ctx.sessionId())
            return SuccessStatus("Run $runId was successfully started.")
        } catch (e: IllegalStateException) {
            throw ErrorStatusException(
                400,
                "Run $runId could not be started because it is in the wrong state (state = ${run.status}).",
                ctx
            )
        } catch (e: IllegalAccessError) {
            throw ErrorStatusException(403, e.message!!, ctx)
        }
    }
}

/**
 * REST handler to move to the next task in a [InteractiveSynchronousCompetition].
 */
class NextTaskCompetitionRunAdminHandler : AbstractCompetitionRunAdminRestHandler(),
    PostRestHandler<SuccessStatus> {
    override val route: String = "run/admin/{runId}/task/next"

    @OpenApi(
        summary = "Moves to and selects the next task. This is a method for admins.",
        path = "/api/v1/run/admin/{runId}/task/next",
        method = HttpMethod.POST,
        pathParams = [OpenApiParam("runId", String::class, "Competition Run ID")],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val runId = runId(ctx)
        val run = getRun(runId) ?: throw ErrorStatusException(404, "Run $runId not found", ctx)

        synchronousAdminCheck(runId, ctx)

        val rac = runActionContext(ctx, run)

        if (run is InteractiveAsynchronousRunManager
            && !AccessManager.rolesOfSession(ctx.sessionId()).contains(RestApiRole.ADMIN)
            && run.currentTask(rac)?.status != TaskRunStatus.ENDED) {
            throw ErrorStatusException(400, "Cannot advance to next task before current task is completed.", ctx)
        }

        try {
            if (run.next(rac)) {
                return SuccessStatus(
                    "Task for run $runId was successfully moved to '${
                        run.currentTaskDescription(
                            rac
                        ).name
                    }'."
                )
            } else {
                throw ErrorStatusException(
                    400,
                    "Task for run $runId could not be changed because there are no tasks left.",
                    ctx
                )
            }
        } catch (e: IllegalStateException) {
            throw ErrorStatusException(
                400,
                "Task for run $runId could not be changed because run is in the wrong state (state = ${run.status}).",
                ctx
            )
        } catch (e: IllegalAccessError) {
            throw ErrorStatusException(403, e.message!!, ctx)
        }
    }
}

/**
 * REST handler to move to the next task in a [InteractiveSynchronousCompetition].
 */
class SwitchTaskCompetitionRunAdminHandler : AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)),
    PostRestHandler<SuccessStatus> {
    override val route: String = "run/admin/{runId}/task/switch/{idx}"

    @OpenApi(
        summary = "Moves to and selects the specified task. This is a method for admins.",
        path = "/api/v1/run/admin/{runId}/task/switch/{idx}",
        method = HttpMethod.POST,
        pathParams = [
            OpenApiParam("runId", String::class, "Competition run ID"),
            OpenApiParam("idx", Int::class, "Index of the task to switch to.")
        ],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val runId = runId(ctx)
        val run = getRun(runId) ?: throw ErrorStatusException(404, "Run $runId not found", ctx)
        val idx = ctx.pathParamMap().getOrElse("idx") {
            throw ErrorStatusException(404, "Parameter 'idx' is missing!'", ctx)
        }.toInt()

        val rac = runActionContext(ctx, run)

        try {
            run.goTo(rac, idx)
            return SuccessStatus(
                "Task for run $runId was successfully moved to '${
                    run.currentTaskDescription(
                        rac
                    ).name
                }'."
            )
        } catch (e: IllegalStateException) {
            throw ErrorStatusException(
                400,
                "Task for run $runId could not be changed because run is in the wrong state (state = ${run.status}).",
                ctx
            )
        } catch (e: IndexOutOfBoundsException) {
            throw ErrorStatusException(
                404,
                "Task for run $runId could not be changed because index $idx is out of bounds for number of available tasks.",
                ctx
            )
        } catch (e: IllegalAccessError) {
            throw ErrorStatusException(403, e.message!!, ctx)
        }
    }
}

/**
 * REST handler to move to the previous task in a [InteractiveSynchronousCompetition].
 */
class PreviousTaskCompetitionRunAdminHandler :
    AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)),
    PostRestHandler<SuccessStatus> {
    override val route: String = "run/admin/{runId}/task/previous"

    @OpenApi(
        summary = "Moves to and selects the previous task. This is a method for admins.",
        path = "/api/v1/run/admin/{runId}/task/previous",
        method = HttpMethod.POST,
        pathParams = [OpenApiParam("runId", String::class, "Competition Run ID")],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val runId = runId(ctx)
        val run = getRun(runId) ?: throw ErrorStatusException(404, "Run $runId not found", ctx)
        val rac = runActionContext(ctx, run)
        try {
            if (run.previous(rac)) {
                return SuccessStatus(
                    "Task for run $runId was successfully moved to '${
                        run.currentTaskDescription(
                            rac
                        ).name
                    }'."
                )
            } else {
                throw ErrorStatusException(
                    400,
                    "Task for run $runId could not be changed because there are no tasks left.",
                    ctx
                )
            }
        } catch (e: IllegalStateException) {
            throw ErrorStatusException(
                400,
                "Task for run $runId could not be changed because run is in the wrong state (state = ${run.status}).",
                ctx
            )
        } catch (e: IllegalAccessError) {
            throw ErrorStatusException(403, e.message!!, ctx)
        }
    }
}

/**
 * REST handler to start the current task in a [InteractiveSynchronousCompetition].
 */
class StartTaskCompetitionRunAdminHandler : AbstractCompetitionRunAdminRestHandler(),
    PostRestHandler<SuccessStatus> {
    override val route: String = "run/admin/{runId}/task/start"

    @OpenApi(
        summary = "Starts the currently active task as a new task run. This is a method for admins.",
        path = "/api/v1/run/admin/{runId}/task/start",
        method = HttpMethod.POST,
        pathParams = [OpenApiParam("runId", String::class, "Competition Run ID")],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val runId = runId(ctx)
        val run = getRun(runId) ?: throw ErrorStatusException(404, "Run $runId not found", ctx)

        synchronousAdminCheck(runId, ctx)

        val rac = runActionContext(ctx, run)
        try {
            run.startTask(rac)
            AuditLogger.taskStart(
                run.id,
                run.currentTask(rac)!!.uid,
                run.currentTaskDescription(rac),
                LogEventSource.REST,
                ctx.sessionId()
            )
            return SuccessStatus("Task '${run.currentTaskDescription(rac).name}' for run $runId was successfully started.")
        } catch (e: IllegalStateException) {
            throw ErrorStatusException(
                400,
                e.message ?: "",
                ctx
            )
        } catch (e: IllegalAccessError) {
            throw ErrorStatusException(403, e.message!!, ctx)
        }
    }
}

/**
 * REST handler to abort the current task in a [InteractiveSynchronousCompetition].
 */
class AbortTaskCompetitionRunAdminHandler : AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)),
    PostRestHandler<SuccessStatus> {
    override val route: String = "run/admin/{runId}/task/abort"

    @OpenApi(
        summary = "Aborts the currently running task run. This is a method for admins.",
        path = "/api/v1/run/admin/{runId}/task/abort",
        method = HttpMethod.POST,
        pathParams = [OpenApiParam("runId", String::class, "Competition Run ID")],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val runId = runId(ctx)
        val run = getRun(runId) ?: throw ErrorStatusException(404, "Run $runId not found", ctx)
        val rac = runActionContext(ctx, run)
        try {
            val task = run.currentTaskDescription(rac)
            run.abortTask(rac)
            AuditLogger.taskEnd(run.id, task.id, task, LogEventSource.REST, ctx.sessionId())
            return SuccessStatus("Task '${run.currentTaskDescription(rac).name}' for run $runId was successfully aborted.")
        } catch (e: IllegalStateException) {
            throw ErrorStatusException(
                400,
                "Task '${run.currentTaskDescription(rac).name}' for run $runId could not be aborted because run is in the wrong state (state = ${run.status}).",
                ctx
            )
        } catch (e: IllegalAccessError) {
            throw ErrorStatusException(403, e.message!!, ctx)
        }
    }
}

/**
 * REST handler to terminate a [InteractiveSynchronousCompetition].
 */
class TerminateCompetitionRunAdminHandler :
    AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)),
    PostRestHandler<SuccessStatus> {
    override val route: String = "run/admin/{runId}/terminate"

    @OpenApi(
        summary = "Terminates a competition run. This is a method for admins.",
        path = "/api/v1/run/admin/{runId}/terminate",
        method = HttpMethod.POST,
        pathParams = [OpenApiParam("runId", String::class, "Competition Run ID")],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val runId = runId(ctx)
        val run = getRun(runId) ?: throw ErrorStatusException(404, "Run $runId not found", ctx)
        val rac = runActionContext(ctx, run)
        try {
            run.end(rac)
            AuditLogger.competitionEnd(run.id, LogEventSource.REST, ctx.sessionId())
            return SuccessStatus("Run $runId was successfully terminated.")
        } catch (e: IllegalStateException) {
            throw ErrorStatusException(
                400,
                "Run $runId could not be terminated because it is in the wrong state (state = ${run.status}).",
                ctx
            )
        } catch (e: IllegalAccessError) {
            throw ErrorStatusException(403, e.message!!, ctx)
        }
    }
}

/**
 * REST handler to adjust a [InteractiveSynchronousCompetition.Task]'s duration.
 */
class AdjustDurationRunAdminHandler :
    AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)),
    PostRestHandler<SuccessStatus> {
    override val route: String = "run/admin/{runId}/adjust/{duration}"

    @OpenApi(
        summary = "Adjusts the duration of a running task run. This is a method for admins.",
        path = "/api/v1/run/admin/{runId}/adjust/{duration}",
        method = HttpMethod.POST,
        pathParams = [
            OpenApiParam("runId", String::class, "Competition Run ID"),
            OpenApiParam("duration", Int::class, "Duration to add.")
        ],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val runId = runId(ctx)
        val run = getRun(runId) ?: throw ErrorStatusException(404, "Run $runId not found", ctx)
        val duration = ctx.pathParamMap().getOrElse("duration") {
            throw ErrorStatusException(404, "Parameter 'duration' is missing!'", ctx)
        }.toInt()
        val rac = runActionContext(ctx, run)
        try {
            run.adjustDuration(rac, duration)
            AuditLogger.taskModified(
                run.id,
                run.currentTaskDescription(rac).name,
                "Task duration adjusted by ${duration}s.",
                LogEventSource.REST,
                ctx.sessionId()
            )
            return SuccessStatus("Duration for run $runId was successfully adjusted.")
        } catch (e: IllegalStateException) {
            throw ErrorStatusException(
                400,
                "Duration for run $runId could not be adjusted because it is in the wrong state (state = ${run.status}).",
                ctx
            )
        } catch (e: IllegalArgumentException) {
            throw ErrorStatusException(
                400,
                "Duration for run $runId could not be adjusted because new duration would drop bellow zero (state = ${run.status}).",
                ctx
            )
        } catch (e: IllegalAccessError) {
            throw ErrorStatusException(403, e.message!!, ctx)
        }
    }
}

/**
 *
 */
class ListPastTasksPerTaskRunAdminHandler :
    AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)),
    GetRestHandler<List<PastTaskInfo>> {
    override val route: String = "run/admin/{runId}/task/past/list"

    @OpenApi(
        summary = "Lists all past tasks for a given run",
        path = "/api/v1/run/admin/{runId}/task/past/list",
        method = HttpMethod.GET,
        pathParams = [
            OpenApiParam("runId", String::class, "Competition Run ID")
        ],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(Array<PastTaskInfo>::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): List<PastTaskInfo> {
        val runId = runId(ctx)
        val run =
            getRun(runId) ?: throw ErrorStatusException(404, "No such run was found: $runId", ctx)

        val rac = runActionContext(ctx, run)

        return run.tasks(rac).filter { it.hasEnded }.map {
            PastTaskInfo(
                taskId = it.uid.string,
                descriptionId = it.description.id.string,
                name = it.description.name,
                taskGroup = it.description.taskGroup.name,
                taskType = it.description.taskType.name,
                numberOfSubmissions = it.submissions.size
            )
        }
    }
}

/**
 *
 */
class ListSubmissionsPerTaskRunAdminHandler :
    AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)),
    GetRestHandler<List<TaskRunSubmissionInfo>> {
    override val route: String = "run/admin/{runId}/submission/list/{taskId}"

    @OpenApi(
        summary = "Lists all submissions for a given task and run.",
        path = "/api/v1/run/admin/{runId}/submission/list/{taskId}",
        method = HttpMethod.GET,
        pathParams = [
            OpenApiParam("runId", String::class, "Competition Run ID"),
            OpenApiParam("taskId", String::class, "Task ID")
        ],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(Array<TaskRunSubmissionInfo>::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): List<TaskRunSubmissionInfo> {
        val runId = runId(ctx)
        val run =
            getRun(runId) ?: throw ErrorStatusException(404, "No such run was found: $runId", ctx)

        val taskId = ctx.pathParamMap().getOrElse("taskId") {
            throw ErrorStatusException(
                404,
                "Parameter 'taskId' is missing!'",
                ctx
            )
        }.UID()
        val teams = run.description.teams.associate { it.uid to it }
        return run.tasks(runActionContext(ctx, run)).filter { it.description.id == taskId }.map {
            TaskRunSubmissionInfo(
                it.uid.string,
                it.submissions.map { sub ->
                    SubmissionInfo(
                        id = sub.uid.string,
                        teamId = sub.teamId.string,
                        teamName = teams[sub.teamId]?.name,
                        memberId = sub.memberId.string,
                        memberName = UserManager.get(sub.memberId)?.username?.name,
                        status = sub.status,
                        timestamp = sub.timestamp,
                        item = if (sub is ItemAspect) RestMediaItem.fromMediaItem(sub.item) else null,
                        text = if (sub is TextAspect) sub.text else null,
                        start = if (sub is TemporalSubmissionAspect) sub.start else null,
                        end = if (sub is TemporalSubmissionAspect) sub.end else null
                    )
                }
            )
        }
    }
}

/**
 *
 */
class OverwriteSubmissionStatusRunAdminHandler :
    AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)),
    PatchRestHandler<SubmissionInfo> {
    override val route: String = "run/admin/{runId}/submission/override"

    @OpenApi(
        summary = "Lists all submissions for a given task and run",
        path = "/api/v1/run/admin/{runId}/submission/override",
        method = HttpMethod.PATCH,
        pathParams = [
            OpenApiParam("runId", String::class, "Competition Run ID")
        ],
        requestBody = OpenApiRequestBody([OpenApiContent(SubmissionInfo::class)]),
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SubmissionInfo::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPatch(ctx: Context): SubmissionInfo {
        val runId = runId(ctx)
        val run =
            getRun(runId) ?: throw ErrorStatusException(404, "No such run was found: $runId", ctx)
        val rac = runActionContext(ctx, run)

        /* Extract HTTP body. */
        val toPatchRest = ctx.bodyAsClass<SubmissionInfo>()
        val submissionId = toPatchRest.id?.UID() ?: throw ErrorStatusException(
            400,
            "No submission ID was specified for update.",
            ctx
        )

        val status = toPatchRest.status

        if (status == SubmissionStatus.INDETERMINATE) {
            throw ErrorStatusException(
                400,
                "Submission Status can not be set to INDETERMINATE",
                ctx
            )
        }

        /* Sanity check to see, whether the submission exists */
        if (run.allSubmissions.none { it.uid == submissionId }) {
            throw ErrorStatusException(404, "The given submission $toPatchRest was not found.", ctx)
        }
        if (run.updateSubmission(rac, submissionId, status)) {
            val submission = run.allSubmissions.single { it.uid == submissionId }
            AuditLogger.overrideSubmission(
                runId,
                submissionId,
                submission.status,
                LogEventSource.REST,
                ctx.sessionId()
            )
            return SubmissionInfo(submission)
        } else {
            throw ErrorStatusException(
                500,
                "Could not update the submission. Please see the backend's log.",
                ctx
            )
        }
    }
}

/**
 * REST handler to list all viewers for a [InteractiveSynchronousCompetition].
 */
class ListViewersRunAdminHandler : AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)),
    GetRestHandler<Array<ViewerInfo>> {
    override val route: String = "run/admin/{runId}/viewer/list"

    @OpenApi(
        summary = "Lists all registered viewers for a competition run. This is a method for admins.",
        path = "/api/v1/run/admin/{runId}/viewer/list",
        method = HttpMethod.GET,
        pathParams = [OpenApiParam("runId", String::class, "Competition Run ID")],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(Array<ViewerInfo>::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): Array<ViewerInfo> {
        val runId = runId(ctx)
        val run = getRun(runId) ?: throw ErrorStatusException(404, "Run $runId not found", ctx)
        return run.viewers()
            .map { ViewerInfo(it.key.sessionId, it.key.userName, it.key.host, it.value) }
            .toTypedArray()
    }
}


/**
 * REST handler to force the viewer state of a viewer instance registered for a [RunManager].
 */
class ForceViewerRunAdminHandler : AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)),
    PostRestHandler<SuccessStatus> {
    override val route: String = "run/admin/{runId}/viewer/list/{viewerId}/force"

    @OpenApi(
        summary = "Forces a viewer with the given viewer ID into the READY state. This is a method for admins.",
        path = "/api/v1/run/admin/{runId}/viewer/list/{viewerId}/force",
        method = HttpMethod.POST,
        pathParams = [
            OpenApiParam("runId", String::class, "Competition Run ID"),
            OpenApiParam("viewerId", String::class, "Viewer ID")
        ],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val runId = runId(ctx)
        val viewerId = ctx.pathParamMap().getOrElse("viewerId") {
            throw ErrorStatusException(404, "Parameter 'viewerId' is missing!'", ctx)
        }
        val run = getRun(runId) ?: throw ErrorStatusException(404, "Run $runId not found", ctx)
        val rac = runActionContext(ctx, run)
        try {
            if (run.overrideReadyState(rac, viewerId)) {
                return SuccessStatus("State for viewer $viewerId forced successfully.")
            } else {
                throw ErrorStatusException(404, "Viewer $viewerId does not exist!'", ctx)
            }
        } catch (e: IllegalStateException) {
            throw ErrorStatusException(
                400,
                "Viewer state for viewer $viewerId (run $runId) could not be enforced because run is in the wrong state (state = ${run.status}).",
                ctx
            )
        }
    }
}

class OverviewRunAdminHandler : AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)), GetRestHandler<AdminRunOverview> {

    override val route = "run/admin/{runId}/overview"
    @OpenApi(
        summary = "Provides a complete overview of a run.",
        path = "/api/v1/run/admin/{runId}/overview",
        method = HttpMethod.GET,
        pathParams = [
            OpenApiParam("runId", String::class, "Competition Run ID"),
        ],
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(AdminRunOverview::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): AdminRunOverview {

        val runId = runId(ctx)

        val run = getRun(runId) ?: throw ErrorStatusException(404, "Run $runId not found", ctx)

        return AdminRunOverview.of(run)
    }

}

class UpdateRunPropertiesAdminHandler : AbstractCompetitionRunAdminRestHandler(setOf(RestApiRole.ADMIN)), PatchRestHandler<SuccessStatus> {

    override val route = "run/admin/{runId}/properties"

    @OpenApi(
        summary = "Changes the properties of a run",
        path = "/api/v1/run/admin/{runId}/properties",
        method = HttpMethod.PATCH,
        pathParams = [
            OpenApiParam("runId", String::class, "Competition Run ID"),
        ],
        requestBody = OpenApiRequestBody([OpenApiContent(RunProperties::class)]),
        tags = ["Competition Run Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPatch(ctx: Context): SuccessStatus {

        val properties = try {
            ctx.bodyAsClass<RunProperties>()
        } catch (e: BadRequestResponse) {
            throw ErrorStatusException(400, "Invalid parameters. This is a programmers error!", ctx)
        }

        val runId = runId(ctx)

        val runManager = RunExecutor.managerForId(runId) ?: throw ErrorStatusException(404, "Run $runId not found", ctx)

        when(runManager) {
            is InteractiveAsynchronousRunManager -> runManager.run.properties = properties
            is InteractiveSynchronousRunManager -> runManager.run.properties = properties
            is NonInteractiveRunManager -> runManager.run.properties = properties
            else -> throw ErrorStatusException(400, "Cannot change properties for ${runManager.javaClass.simpleName}", ctx)
        }

        //TODO trigger persistence of run

        return SuccessStatus("Properties updated")

    }

}