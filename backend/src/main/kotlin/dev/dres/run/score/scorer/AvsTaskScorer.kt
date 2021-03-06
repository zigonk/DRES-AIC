package dev.dres.run.score.scorer

import dev.dres.data.model.UID
import dev.dres.data.model.basics.media.MediaItem
import dev.dres.data.model.competition.TeamId
import dev.dres.data.model.submissions.Submission
import dev.dres.data.model.submissions.SubmissionStatus
import dev.dres.run.score.interfaces.RecalculatingTaskScorer
import dev.dres.utilities.TimeUtil
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class AvsTaskScorer: RecalculatingTaskScorer {

    private var lastScores: Map<TeamId, Double> = emptyMap()
    private val lastScoresLock = ReentrantReadWriteLock()

    override fun computeScores(submissions: Collection<Submission>, teamIds: Collection<TeamId>, taskStartTime: Long, taskDuration: Long, taskEndTime: Long): Map<UID, Double> {

        val correctSubmissions = submissions.filter { it.status == SubmissionStatus.CORRECT }
        val wrongSubmissions = submissions.filter { it.status == SubmissionStatus.WRONG }

        val correctSubmissionsPerTeam = correctSubmissions.groupBy { it.teamId }
        val wrongSubmissionsPerTeam = wrongSubmissions.groupBy { it.teamId }

        val totalCorrectQuantized = countQuantized(correctSubmissions).toDouble()

        lastScores = this.lastScoresLock.write {
            teamIds.map { teamid ->

                val correctSubs = correctSubmissionsPerTeam[teamid] ?: return@map teamid to 0.0

                val correct = correctSubs.size

                val wrong = wrongSubmissionsPerTeam[teamid]?.size ?: 0

                teamid to 100.0 *
                        (correct / (correct + wrong / 2.0)) *
                        (countQuantized(correctSubs).toDouble() / totalCorrectQuantized)
            }.toMap()
        }


        return scores()
    }

    private fun countQuantized(submissions: Collection<Submission>): Int {

        return submissions.groupBy { it.item }.map {
            when(it.key) {
                is MediaItem.ImageItem -> 1
                is MediaItem.VideoItem -> {
                    val ranges = it.value.map { s -> (s as Submission.Temporal).temporalRange }
                    TimeUtil.merge(ranges, overlap = 1).size
                }
            }
        }.sum()

    }

    override fun scores(): Map<UID, Double> = this.lastScoresLock.read { this.lastScores }
}