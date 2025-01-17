package dres.run.score.scorer

import dev.dres.data.model.UID
import dev.dres.data.model.basics.media.MediaItem
import dev.dres.data.model.submissions.Submission
import dev.dres.data.model.submissions.SubmissionStatus
import dev.dres.run.score.TaskContext
import dev.dres.run.score.scorer.AvsTaskScorer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class LegacyAvsTaskScorerTest {

    private lateinit var scorer: AvsTaskScorer
    private val teams = listOf(UID(), UID(), UID()) //3 random team ids
    private val defaultTaskDuration = 5 * 60L
    private val dummyImageItems: List<MediaItem.ImageItem>
    private val dummyVideoItems: List<MediaItem.VideoItem>

    init {
        val collectionId = UID()

        dummyImageItems = listOf(
            MediaItem.ImageItem(UID(), "Image 1", "images/1", collectionId),
            MediaItem.ImageItem(UID(), "Image 2", "images/2", collectionId),
            MediaItem.ImageItem(UID(), "Image 3", "images/3", collectionId),
            MediaItem.ImageItem(UID(), "Image 4", "images/4", collectionId),
            MediaItem.ImageItem(UID(), "Image 5", "images/5", collectionId),
            MediaItem.ImageItem(UID(), "Image 6", "images/6", collectionId)
        )

        dummyVideoItems = listOf(
            MediaItem.VideoItem(UID(), "Video 1", "video/1", collectionId, 10 * 60 * 1000, 24f),
            MediaItem.VideoItem(UID(), "Video 2", "video/2", collectionId, 20 * 60 * 1000, 24f),
            MediaItem.VideoItem(UID(), "Video 3", "video/3", collectionId, 30 * 60 * 1000, 24f),
            MediaItem.VideoItem(UID(), "Video 4", "video/4", collectionId, 40 * 60 * 1000, 24f),
            MediaItem.VideoItem(UID(), "Video 5", "video/5", collectionId, 50 * 60 * 1000, 24f)
        )

    }

    @BeforeEach
    fun setup() {
        this.scorer = AvsTaskScorer()
    }

    @Test
    fun noSubmissions() {
        val scores = this.scorer.computeScores(emptyList(), TaskContext(teams, 100_000, defaultTaskDuration))
        assertEquals(0.0, scores[teams[0]])
        assertEquals(0.0, scores[teams[1]])
        assertEquals(0.0, scores[teams[2]])
    }

    @Test
    fun allWrongSameImage() {
        val taskStartTime = System.currentTimeMillis() - 100_000
        val submissions = listOf(
            Submission.Item(teams[0], UID(), taskStartTime + 1000, dummyImageItems[0]).also { it.status = SubmissionStatus.WRONG },
            Submission.Item(teams[1], UID(), taskStartTime + 2000, dummyImageItems[0]).also { it.status = SubmissionStatus.WRONG },
            Submission.Item(teams[2], UID(), taskStartTime + 3000, dummyImageItems[0]).also { it.status = SubmissionStatus.WRONG }
        )
        val scores = this.scorer.computeScores(submissions, TaskContext(teams, taskStartTime, defaultTaskDuration))
        assertEquals(0.0, scores[teams[0]])
        assertEquals(0.0, scores[teams[1]])
        assertEquals(0.0, scores[teams[2]])
    }

    @Test
    fun allWrongDifferentImages() {
        val taskStartTime = System.currentTimeMillis() - 100_000
        val submissions = listOf(
            Submission.Item(teams[0], UID(), taskStartTime + 1000, dummyImageItems[0]).also { it.status = SubmissionStatus.WRONG },
            Submission.Item(teams[1], UID(), taskStartTime + 2000, dummyImageItems[1]).also { it.status = SubmissionStatus.WRONG },
            Submission.Item(teams[2], UID(), taskStartTime + 3000, dummyImageItems[2]).also { it.status = SubmissionStatus.WRONG }
        )
        val scores = this.scorer.computeScores(submissions, TaskContext(teams, taskStartTime, defaultTaskDuration))
        assertEquals(0.0, scores[teams[0]])
        assertEquals(0.0, scores[teams[1]])
        assertEquals(0.0, scores[teams[2]])
    }

    @Test
    fun allSameImageAllCorrect() {
        val taskStartTime = System.currentTimeMillis() - 100_000
        val submissions = listOf(
            Submission.Item(teams[0], UID(), taskStartTime + 1000, dummyImageItems[0]).also { it.status = SubmissionStatus.CORRECT },
            Submission.Item(teams[1], UID(), taskStartTime + 2000, dummyImageItems[0]).also { it.status = SubmissionStatus.CORRECT },
            Submission.Item(teams[2], UID(), taskStartTime + 3000, dummyImageItems[0]).also { it.status = SubmissionStatus.CORRECT }
        )
        val scores = this.scorer.computeScores(submissions, TaskContext(teams, taskStartTime, defaultTaskDuration))
        assertEquals(100.0, scores[teams[0]])
        assertEquals(100.0, scores[teams[1]])
        assertEquals(100.0, scores[teams[2]])
    }

    @Test
    fun allDifferentImageAllCorrect() {
        val taskStartTime = System.currentTimeMillis() - 100_000
        val submissions = listOf(
            Submission.Item(teams[0], UID(), taskStartTime + 1000, dummyImageItems[0]).also { it.status = SubmissionStatus.CORRECT },
            Submission.Item(teams[1], UID(), taskStartTime + 2000, dummyImageItems[1]).also { it.status = SubmissionStatus.CORRECT },
            Submission.Item(teams[2], UID(), taskStartTime + 3000, dummyImageItems[2]).also { it.status = SubmissionStatus.CORRECT }
        )
        val scores = this.scorer.computeScores(submissions, TaskContext(teams, taskStartTime, defaultTaskDuration))
        assertEquals(100.0 / 3.0, scores[teams[0]]!!, 0.001)
        assertEquals(100.0 / 3.0, scores[teams[1]]!!, 0.001)
        assertEquals(100.0 / 3.0, scores[teams[2]]!!, 0.001)
    }

    @Test
    fun someDifferentImageSomeCorrect() {
        val taskStartTime = System.currentTimeMillis() - 100_000
        val submissions = listOf(

            /*
            total correct: 4 (0, 1, 2, 3)
            total wrong: 2 (4, 5)
             */

            //3 out of 4 correct
            Submission.Item(teams[0], UID(), taskStartTime + 1000, dummyImageItems[0]).also { it.status = SubmissionStatus.CORRECT },
            Submission.Item(teams[0], UID(), taskStartTime + 2000, dummyImageItems[1]).also { it.status = SubmissionStatus.CORRECT },
            Submission.Item(teams[0], UID(), taskStartTime + 3000, dummyImageItems[2]).also { it.status = SubmissionStatus.CORRECT },
            Submission.Item(teams[0], UID(), taskStartTime + 4000, dummyImageItems[4]).also { it.status = SubmissionStatus.WRONG },


            //1 out of 3 correct
            Submission.Item(teams[1], UID(), taskStartTime + 1000, dummyImageItems[0]).also { it.status = SubmissionStatus.CORRECT },
            Submission.Item(teams[1], UID(), taskStartTime + 2000, dummyImageItems[5]).also { it.status = SubmissionStatus.WRONG },
            Submission.Item(teams[1], UID(), taskStartTime + 3000, dummyImageItems[4]).also { it.status = SubmissionStatus.WRONG },

            //1 out of 2 correct
            Submission.Item(teams[2], UID(), taskStartTime + 2000, dummyImageItems[3]).also { it.status = SubmissionStatus.CORRECT },
            Submission.Item(teams[2], UID(), taskStartTime + 3000, dummyImageItems[5]).also { it.status = SubmissionStatus.WRONG }
        )


        val scores = this.scorer.computeScores(submissions, TaskContext(teams, taskStartTime, defaultTaskDuration))

        /*
        c = q(c) = 3, i = 1, q(p) = 4

        100 * 3   3
        ------- * - = 64.28571428571429
        3 + 1/2   4
         */
        assertEquals(64.28571428571429, scores[teams[0]]!!, 0.001)

        /*
        c = q(c) = 1, i = 1, q(p) = 4

        100 * 1   1
        ------- * - = 12.5
        1 + 2/2   4
         */
        assertEquals(12.5, scores[teams[1]]!!, 0.001)

        /*
        c = q(c) = 3, i = 1, q(p) = 4

        100 * 1   1
        ------- * - = 16.66666666666667
        1 + 1/2   4
         */
        assertEquals(16.66666666666667, scores[teams[2]]!!, 0.001)
    }


    @Test
    fun allSameVideoSameSegmentAllCorrect() {
        val taskStartTime = System.currentTimeMillis() - 100_000
        val submissions = listOf(
            Submission.Temporal(teams[0], UID(), taskStartTime + 1000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[1], UID(), taskStartTime + 2000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[2], UID(), taskStartTime + 3000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT }
        )
        val scores = this.scorer.computeScores(submissions, TaskContext(teams, taskStartTime, defaultTaskDuration))
        assertEquals(100.0, scores[teams[0]])
        assertEquals(100.0, scores[teams[1]])
        assertEquals(100.0, scores[teams[2]])
    }


    @Test
    fun allSameVideoDifferentSegmentAllCorrect() {
        val taskStartTime = System.currentTimeMillis() - 100_000
        val submissions = listOf(
            Submission.Temporal(teams[0], UID(), taskStartTime + 1000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[1], UID(), taskStartTime + 2000, dummyVideoItems[0], 30_000, 40_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[2], UID(), taskStartTime + 3000, dummyVideoItems[0], 50_000, 60_000).also { it.status = SubmissionStatus.CORRECT }
        )
        val scores = this.scorer.computeScores(submissions, TaskContext(teams, taskStartTime, defaultTaskDuration))
        assertEquals(33.33333333333333, scores[teams[0]]!!, 0.0001)
        assertEquals(33.33333333333333, scores[teams[1]]!!, 0.0001)
        assertEquals(33.33333333333333, scores[teams[2]]!!, 0.0001)
    }


    @Test
    fun allDifferentVideoDifferentSegmentAllCorrect() {
        val taskStartTime = System.currentTimeMillis() - 100_000
        val submissions = listOf(
            Submission.Temporal(teams[0], UID(), taskStartTime + 1000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[1], UID(), taskStartTime + 2000, dummyVideoItems[1], 30_000, 40_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[2], UID(), taskStartTime + 3000, dummyVideoItems[2], 50_000, 60_000).also { it.status = SubmissionStatus.CORRECT }
        )
        val scores = this.scorer.computeScores(submissions, TaskContext(teams, taskStartTime, defaultTaskDuration))
        assertEquals(33.33333333333333, scores[teams[0]]!!, 0.0001)
        assertEquals(33.33333333333333, scores[teams[1]]!!, 0.0001)
        assertEquals(33.33333333333333, scores[teams[2]]!!, 0.0001)
    }

    @Test
    fun allSameVideoOverlappingSegmentAllCorrect() {
        val taskStartTime = System.currentTimeMillis() - 100_000
        val submissions = listOf(
            Submission.Temporal(teams[0], UID(), taskStartTime + 1000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[0], UID(), taskStartTime + 2000, dummyVideoItems[0], 11_000, 21_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[0], UID(), taskStartTime + 3000, dummyVideoItems[0], 12_000, 22_000).also { it.status = SubmissionStatus.CORRECT },

            Submission.Temporal(teams[1], UID(), taskStartTime + 1000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[1], UID(), taskStartTime + 2000, dummyVideoItems[0], 11_000, 21_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[1], UID(), taskStartTime + 3000, dummyVideoItems[0], 12_000, 22_000).also { it.status = SubmissionStatus.CORRECT },

            Submission.Temporal(teams[2], UID(), taskStartTime + 1000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[2], UID(), taskStartTime + 2000, dummyVideoItems[0], 11_000, 21_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[2], UID(), taskStartTime + 3000, dummyVideoItems[0], 12_000, 22_000).also { it.status = SubmissionStatus.CORRECT }
        )
        val scores = this.scorer.computeScores(submissions, TaskContext(teams, taskStartTime, defaultTaskDuration))
        assertEquals(100.0, scores[teams[0]])
        assertEquals(100.0, scores[teams[1]])
        assertEquals(100.0, scores[teams[2]])
    }

    @Test
    fun allSameVideoOverlappingSegmentSomeCorrect() {
        val taskStartTime = System.currentTimeMillis() - 100_000
        val submissions = listOf(
            Submission.Temporal(teams[0], UID(), taskStartTime + 1000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[0], UID(), taskStartTime + 2000, dummyVideoItems[0], 11_000, 21_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[0], UID(), taskStartTime + 3000, dummyVideoItems[0], 12_000, 22_000).also { it.status = SubmissionStatus.CORRECT },

            Submission.Temporal(teams[1], UID(), taskStartTime + 1000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[1], UID(), taskStartTime + 2000, dummyVideoItems[0], 11_000, 21_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[1], UID(), taskStartTime + 3000, dummyVideoItems[0], 12_000, 22_000).also { it.status = SubmissionStatus.CORRECT },

            Submission.Temporal(teams[2], UID(), taskStartTime + 1000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[2], UID(), taskStartTime + 2000, dummyVideoItems[0], 11_000, 21_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[2], UID(), taskStartTime + 3000, dummyVideoItems[0], 12_000, 22_000).also { it.status = SubmissionStatus.CORRECT },

            Submission.Temporal(teams[0], UID(), taskStartTime + 1000, dummyVideoItems[3], 10_000, 20_000).also { it.status = SubmissionStatus.WRONG },
            Submission.Temporal(teams[1], UID(), taskStartTime + 2000, dummyVideoItems[3], 10_000, 20_000).also { it.status = SubmissionStatus.WRONG },
            Submission.Temporal(teams[2], UID(), taskStartTime + 3000, dummyVideoItems[3], 10_000, 20_000).also { it.status = SubmissionStatus.WRONG }


        )
        val scores = this.scorer.computeScores(submissions, TaskContext(teams, taskStartTime, defaultTaskDuration))

        assertEquals(85.71428571428571, scores[teams[0]]!!, 0.001)
        assertEquals(85.71428571428571, scores[teams[1]]!!, 0.001)
        assertEquals(85.71428571428571, scores[teams[2]]!!, 0.001)
    }


    @Test
    fun partiallyMergedSubmissionSomeCorrect() {
        val taskStartTime = System.currentTimeMillis() - 100_000
        val submissions = listOf(
            //team 1
            //gets merged to one
            Submission.Temporal(teams[0], UID(), taskStartTime + 1000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[0], UID(), taskStartTime + 2000, dummyVideoItems[0], 20_001, 25_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[0], UID(), taskStartTime + 3000, dummyVideoItems[0], 25_001, 30_000).also { it.status = SubmissionStatus.CORRECT },
            //plus 2 independent correct ones
            Submission.Temporal(teams[0], UID(), taskStartTime + 4000, dummyVideoItems[1], 5_000, 6_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[0], UID(), taskStartTime + 5000, dummyVideoItems[2], 3_000, 4_000).also { it.status = SubmissionStatus.CORRECT },
            //and an incorrect one directly next to it
            Submission.Temporal(teams[0], UID(), taskStartTime + 6000, dummyVideoItems[2], 4_001, 5_000).also { it.status = SubmissionStatus.WRONG },

            //c = 5, q(c) = 3, i = 1


            //team 2
            //the center part is missing, so it's counted as two in the quantization
            Submission.Temporal(teams[1], UID(), taskStartTime + 1000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[1], UID(), taskStartTime + 3000, dummyVideoItems[0], 25_001, 30_000).also { it.status = SubmissionStatus.CORRECT },
            //another correct one, same as team 1
            Submission.Temporal(teams[1], UID(), taskStartTime + 4000, dummyVideoItems[1], 5_000, 6_000).also { it.status = SubmissionStatus.CORRECT },
            //and two wrong ones
            Submission.Temporal(teams[1], UID(), taskStartTime + 6000, dummyVideoItems[3], 0, 5_000).also { it.status = SubmissionStatus.WRONG },
            Submission.Temporal(teams[1], UID(), taskStartTime + 6000, dummyVideoItems[3], 10_000, 15_000).also { it.status = SubmissionStatus.WRONG },

            //c = 3, q(c) = 3, i = 2

            //team 3
            //same as team 2, but with all 3 segments of the 1st video, same as team 1
            Submission.Temporal(teams[2], UID(), taskStartTime + 1000, dummyVideoItems[0], 10_000, 20_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[2], UID(), taskStartTime + 2000, dummyVideoItems[0], 20_001, 25_000).also { it.status = SubmissionStatus.CORRECT },
            Submission.Temporal(teams[2], UID(), taskStartTime + 3000, dummyVideoItems[0], 25_001, 30_000).also { it.status = SubmissionStatus.CORRECT },
            //another correct one, same as team 1
            Submission.Temporal(teams[2], UID(), taskStartTime + 4000, dummyVideoItems[1], 5_000, 6_000).also { it.status = SubmissionStatus.CORRECT },
            //and two wrong ones
            Submission.Temporal(teams[2], UID(), taskStartTime + 6000, dummyVideoItems[3], 0, 5_000).also { it.status = SubmissionStatus.WRONG },
            Submission.Temporal(teams[2], UID(), taskStartTime + 6000, dummyVideoItems[3], 10_000, 15_000).also { it.status = SubmissionStatus.WRONG },

            //c = 4, q(c) = 2, i = 2

        )
        val scores = this.scorer.computeScores(submissions, TaskContext(teams, taskStartTime, defaultTaskDuration))

        /*
        c = 5, q(c) = 3, i = 1, q(p) = 3

        100 * 5   3
        ------- * - = 90.909090909090
        5 + 1/2   3
         */
        assertEquals(90.909090909090, scores[teams[0]]!!, 0.001)

        /*
        c = 3, q(c) = 3, i = 2, q(p) = 3

        100 * 3   3
        ------- * - = 75
        3 + 2/2   3
         */
        assertEquals(75.0, scores[teams[1]])

        /*
        c = 4, q(c) = 2, i = 2, q(p) = 3

        100 * 4   2
        ------- * - = 53.33333333333
        4 + 2/2   3
         */
        assertEquals(53.33333333333, scores[teams[2]]!!, 0.001)
    }

}
