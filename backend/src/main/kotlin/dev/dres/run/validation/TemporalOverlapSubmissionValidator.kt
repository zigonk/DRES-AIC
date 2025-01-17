package dev.dres.run.validation

import dev.dres.data.model.competition.TaskDescription
import dev.dres.data.model.competition.VideoSegment
import dev.dres.data.model.submissions.Submission
import dev.dres.data.model.submissions.SubmissionStatus
import dev.dres.data.model.submissions.aspects.TemporalSubmissionAspect
import dev.dres.run.validation.interfaces.SubmissionValidator

/**
 * A validator class that checks, if a submission is correct based on the target segment and the
 * temporal overlap of the [Submission] with the provided [MediaSegmentTaskDescription].
 *
 * @author Luca Rossetto & Ralph Gasser
 * @version 1.0
 */
class TemporalOverlapSubmissionValidator(private val targetSegment: VideoSegment) : SubmissionValidator {

    /**
     * Validates a [Submission] based on the target segment and the temporal overlap of the
     * [Submission] with the [TaskDescription].
     *
     * @param submission The [Submission] to validate.
     */
    override fun validate(submission: Submission){
        if (submission !is TemporalSubmissionAspect){
            submission.status = SubmissionStatus.WRONG
            return
        }
        submission.status = when {
            submission.start > submission.end -> SubmissionStatus.WRONG
            submission.item != targetSegment.item ->  SubmissionStatus.WRONG
            else -> {
                val outer = this.targetSegment.temporalRange.toMilliseconds()
                if ((outer.first <= submission.start && outer.second >= submission.start)  || (outer.first <= submission.end && outer.second >= submission.end)) {
                    SubmissionStatus.CORRECT
                } else {
                    SubmissionStatus.WRONG
                }
            }
        }
    }

    override val deferring: Boolean
        get() = false
}