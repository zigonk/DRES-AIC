package dres.api.rest.handler

import dres.api.rest.AccessManager
import dres.api.rest.RestApiRole
import dres.api.rest.types.run.RunInfo
import dres.api.rest.types.run.RunState
import dres.api.rest.types.status.ErrorStatus
import dres.api.rest.types.status.ErrorStatusException
import dres.data.model.competition.Task
import dres.data.model.competition.TaskType
import dres.data.model.run.Submission
import dres.data.model.run.SubmissionStatus
import dres.run.RunExecutor
import dres.run.RunManager
import dres.run.score.ScoreOverview
import dres.utilities.extensions.errorResponse
import io.javalin.core.security.Role
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.OpenApi
import io.javalin.plugin.openapi.annotations.OpenApiContent
import io.javalin.plugin.openapi.annotations.OpenApiParam
import io.javalin.plugin.openapi.annotations.OpenApiResponse

abstract class AbstractCompetitionRunRestHandler : RestHandler, AccessManagedRestHandler {

    override val permittedRoles: Set<Role> = setOf(RestApiRole.VIEWER)

    private fun userId(ctx: Context): Long = AccessManager.getUserIdforSession(ctx.req.session.id)!!

    private fun isAdmin(ctx: Context): Boolean = AccessManager.rolesOfSession(ctx.req.session.id).contains(RestApiRole.ADMIN)

//    /**
//     * returns the runs visible to the current user
//     */
//    fun getRuns(ctx: Context): List<CompetitionRun> {
//        val userId = userId(ctx)
//        return runs.filter { it.competition.teams.any { it.users.contains(userId) } }
//    }

    fun getRelevantManagers(ctx: Context): List<RunManager> {
        if (isAdmin(ctx)){
            return RunExecutor.managers()
        }
        val userId = userId(ctx)
        return RunExecutor.managers().filter { it.competition.teams.any { it.users.contains(userId) } }
    }

    fun getRun(ctx: Context, runId: Long): RunManager? {
        if (isAdmin(ctx)){
            return RunExecutor.managerForId(runId)
        }
        val userId = userId(ctx)
        val run = RunExecutor.managerForId(runId) ?: return null
        if (run.competition.teams.any { it.users.contains(userId) }){
            return run
        }
        return null
    }

    fun runId(ctx: Context) = ctx.pathParamMap().getOrElse("runId") {
        throw ErrorStatusException(404, "Parameter 'runId' is missing!'")
    }.toLong()
}

class ListCompetitionRunInfosHandler : AbstractCompetitionRunRestHandler(), GetRestHandler<List<RunInfo>> {

    override val route = "run/info"

    @OpenApi(
            summary = "Lists an overview of all competition runs visible to the current user",
            path = "/api/run/info",
            tags = ["Competition Run"],
            responses = [
                OpenApiResponse("200", [OpenApiContent(Array<RunInfo>::class)]),
                OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
            ]
    )
    override fun doGet(ctx: Context): List<RunInfo> = getRelevantManagers(ctx).map { RunInfo(it) }
}

class ListCompetitionRunStatesHandler : AbstractCompetitionRunRestHandler(), GetRestHandler<List<RunState>> {

    override val route = "run/state"

    @OpenApi(
            summary = "Lists an overview of all competition runs visible to the current user",
            path = "/api/run/state",
            tags = ["Competition Run"],
            responses = [
                OpenApiResponse("200", [OpenApiContent(Array<RunState>::class)]),
                OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
            ]
    )
    override fun doGet(ctx: Context): List<RunState> = getRelevantManagers(ctx).map { RunState(it) }

}


class GetCompetitionRunInfoHandler : AbstractCompetitionRunRestHandler(), GetRestHandler<RunInfo> {

    override val route = "run/info/:runId"

    @OpenApi(
            summary = "Returns a specific competition run.",
            path = "/api/run/info/:runId",
            tags = ["Competition Run"],
            pathParams = [OpenApiParam("runId", Long::class, "Competition Run ID")],
            responses = [
                OpenApiResponse("200", [OpenApiContent(RunInfo::class)]),
                OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
            ]
    )
    override fun doGet(ctx: Context): RunInfo {

        val runId = runId(ctx)

        val run = getRun(ctx, runId)

        if (run != null){
            return RunInfo(run)
        }

        throw ErrorStatusException(404, "Run $runId not found")
    }
}

class GetCompetitionRunStateHandler : AbstractCompetitionRunRestHandler(), GetRestHandler<RunState> {

    override val route = "run/state/:runId"

    @OpenApi(
            summary = "Returns the state of a specific competition run.",
            path = "/api/run/state/:runId",
            tags = ["Competition Run"],
            pathParams = [OpenApiParam("runId", Long::class, "Competition Run ID")],
            responses = [
                OpenApiResponse("200", [OpenApiContent(RunState::class)]),
                OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
            ]
    )
    override fun doGet(ctx: Context): RunState {
        val runId = runId(ctx)
        val run = getRun(ctx, runId)

        if (run != null){
            return RunState(run)
        }
        throw ErrorStatusException(404, "Run $runId not found")
    }
}


class ListCompetitionScoreHandler : AbstractCompetitionRunRestHandler(), GetRestHandler<List<ScoreOverview>> {

    override val route = "run/score/:runId/"

    @OpenApi(
            summary = "Returns the score overviews of a specific competition run.",
            path = "/api/run/score/:runId",
            tags = ["Competition Run"],
            pathParams = [OpenApiParam("runId", Long::class, "Competition Run ID")],
            responses = [
                OpenApiResponse("200", [OpenApiContent(Array<ScoreOverview>::class)]),
                OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
            ]
    )
    override fun doGet(ctx: Context): List<ScoreOverview> {
        val runId = runId(ctx)
        val run = getRun(ctx, runId) ?: throw ErrorStatusException(404, "Run $runId not found")
        return run.scoreboards?.map { it.overview() } ?: throw  ErrorStatusException(400, "Not scoreboards available. There is probably no run going on.")
    }
}

class CurrentTaskScoreHandler : AbstractCompetitionRunRestHandler(), GetRestHandler<List<ScoreOverview>> {

    override val route = "run/:runId/score/task"

    @OpenApi(
            summary = "Returns the overviews of all score boards for the current task.",
            path = "/api/run/:runId/score/task",
            tags = ["Competition Run"],
            pathParams = [OpenApiParam("runId", Long::class, "Competition Run ID")],
            responses = [
                OpenApiResponse("200", [OpenApiContent(Array<ScoreOverview>::class)]),
                OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
            ]
    )
    override fun doGet(ctx: Context): List<ScoreOverview> {
        val runId = runId(ctx)
        val run = getRun(ctx, runId) ?: throw ErrorStatusException(404, "Run $runId not found")
        return run.scoreboards?.map { it.taskOverview() } ?: throw  ErrorStatusException(400, "Not scoreboards available. There is probably no run going on.")
    }
}

data class TaskInfo(val name: String, val taskGroup: String, val type: TaskType, val duration: Long) {
    companion object{
        fun of(task: Task, duration: Long): TaskInfo = TaskInfo(task.name, task.taskGroup, task.description.taskType, duration)
    }
}

class CurrentTaskInfoHandler : AbstractCompetitionRunRestHandler(), GetRestHandler<TaskInfo> {

    override val route = "run/:runId/task"

    @OpenApi(
            summary = "Returns the information for the current task.",
            path = "/api/run/:runId/task",
            tags = ["Competition Run"],
            pathParams = [OpenApiParam("runId", Long::class, "Competition Run ID")],
            responses = [
                OpenApiResponse("200", [OpenApiContent(TaskInfo::class)]),
                OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
            ]
    )
    override fun doGet(ctx: Context): TaskInfo {

        val runId = runId(ctx)

        val run = getRun(ctx, runId) ?: throw ErrorStatusException(404, "Run $runId not found")

        val task = run.currentTask ?: throw ErrorStatusException(404, "No active task in run $runId")

        return TaskInfo.of(task, task.description.taskType.defaultDuration) //FIXME get task duration

    }
}

class CurrentQueryHandler : AbstractCompetitionRunRestHandler(), GetRestHandler<Any> {

    override val route = "run/:runId/query"

    @OpenApi(
            summary = "Returns the actual query for the current task.",
            path = "/api/run/:runId/query",
            tags = ["Competition Run"],
            pathParams = [OpenApiParam("runId", Long::class, "Competition Run ID")],
            responses = [
                OpenApiResponse("200"),
                OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
            ]
    )
    override fun get(ctx: Context) {

        try {
            val runId = runId(ctx)

            val run = getRun(ctx, runId) ?: throw ErrorStatusException(404, "Run $runId not found")

            val task = run.currentTask ?: throw ErrorStatusException(404, "No active task in run $runId")


            //TODO return the actual content for the task
            throw ErrorStatusException(500, "not yet implemented")

        }catch (e: ErrorStatusException) {
            ctx.errorResponse(e)
        }

    }

    /* UNUSED */
    override fun doGet(ctx: Context) = ""
}


class CurrentSubmissionInfoHandler : AbstractCompetitionRunRestHandler(), GetRestHandler<List<Submission>> {

    override val route = "run/:runId/task/submissions" //TODO add a second handler with a time parameter to only get 'new' submissions

    @OpenApi(
            summary = "Returns the submissions to the current task.",
            path = "/api/run/:runId/task/submissions",
            tags = ["Competition Run"],
            pathParams = [OpenApiParam("runId", Long::class, "Competition Run ID")],
            responses = [
                OpenApiResponse("200", [OpenApiContent(Array<Submission>::class)]),
                OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
            ]
    )
    override fun doGet(ctx: Context): List<Submission> {
        val runId = runId(ctx)
        val run = getRun(ctx, runId) ?: throw ErrorStatusException(404, "Run $runId not found")
        return run.submissions ?: throw  ErrorStatusException(400, "Not submissions available. There is probably no run going on.")
    }
}


