package dev.dres.run.filter

import dev.dres.data.model.submissions.Submission
import dev.dres.data.model.submissions.SubmissionStatus

class CorrectPerTeamFilter(private val limit: Int = 1) : SubmissionFilter {
    constructor(parameters: Map<String, String>) : this(parameters.getOrDefault("limit", "1").toIntOrNull() ?: 1)

    override val reason = "Maximum number of correct submissions ($limit) exceeded for the team"

    override fun test(submission: Submission): Boolean = submission.task!!.submissions.count { it.status == SubmissionStatus.CORRECT && it.teamId == submission.teamId } < limit
}