package dres.api.rest.handler

import dres.api.rest.RestApiRole
import dres.api.rest.types.status.ErrorStatus
import dres.api.rest.types.status.ErrorStatusException
import dres.api.rest.types.status.SuccessStatus
import dres.data.dbo.DAO
import dres.data.model.basics.media.MediaCollection
import dres.data.model.run.SubmissionStatus
import dres.run.RunExecutor
import io.javalin.core.security.Role
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.*

abstract class AbstractJudgementHandler : RestHandler, AccessManagedRestHandler {
    override val permittedRoles: Set<Role> = setOf(RestApiRole.JUDGE)

    protected fun runId(ctx: Context) = ctx.pathParamMap().getOrElse("runId") {
        throw ErrorStatusException(400, "Parameter 'runId' is missing!'")
    }.toLong()
}

data class Judgement(val token: String, val validator: String, val verdict: SubmissionStatus)

data class JudgementRequest(val token: String, val validator: String, val collection: String, val item: String, val startTime: String?, val endTime: String?)

class NextOpenJudgementHandler(val collections: DAO<MediaCollection>) : AbstractJudgementHandler(), GetRestHandler<JudgementRequest> {
    override val route = "run/:runId/judge/next"

    @OpenApi(
            summary = "Gets the next open Submission to be judged.",
            path = "/api/run/:runId/judge/next",
            tags = ["Judgement"],
            responses = [
                OpenApiResponse("200", [OpenApiContent(JudgementRequest::class)]),
                OpenApiResponse("202", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
            ]
    )
    override fun doGet(ctx: Context): JudgementRequest {
        val runId = this.runId(ctx)
        val run = RunExecutor.managerForId(runId) ?: throw ErrorStatusException(404, "Run $runId not found")

        val validator = run.judgementValidators.find { it.hasOpen } ?: throw ErrorStatusException(202, "There is currently no submission awaiting judgement")
        val next = validator.next(ctx.req.session.id) ?: throw ErrorStatusException(202, "There is currently no submission awaiting judgement")

        val collection = this.collections[next.second.item.collection] ?: throw ErrorStatusException(404, "Could not find collection with id ${next.second.item.collection}")

        return JudgementRequest(next.first, validator.id, collection.name, next.second.item.name, next.second.start?.toString(), next.second.end?.toString())
    }
}

class PostJudgementHandler : AbstractJudgementHandler(), PostRestHandler<SuccessStatus> {
    override val route = "run/:runId/judge"

    @OpenApi(
            summary = "Returns a Judgement.",
            path = "/api/run/:runId/judge", method = HttpMethod.POST,
            requestBody = OpenApiRequestBody([OpenApiContent(Judgement::class)]),
            tags = ["Judgement"],
            responses = [
                OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
                OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
                OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
            ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val runId = this.runId(ctx)
        val run = RunExecutor.managerForId(runId) ?: throw ErrorStatusException(404, "Run $runId not found")
        val judgement = try {
            ctx.bodyAsClass(Judgement::class.java)
        } catch (e: BadRequestResponse) {
            throw ErrorStatusException(400, "Invalid parameters. This is a programmers error!")
        }

        val validator = run.judgementValidators.find { it.id == judgement.validator } ?: throw ErrorStatusException(404, "no matching task found with validator ${judgement.validator}")

        validator.judge(judgement.token, judgement.verdict)

        return SuccessStatus("Verdict received and accepted. Thanks!")
    }
}