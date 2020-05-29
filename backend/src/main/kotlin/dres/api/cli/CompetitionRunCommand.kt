package dres.api.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long

import dres.data.dbo.DAO
import dres.data.model.run.CompetitionRun
import dres.run.RunExecutor
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class CompetitionRunCommand(internal val runs: DAO<CompetitionRun>) : NoOpCliktCommand(name = "runs") {

    init {
        subcommands(OngoingCompetitionRunsCommand(), ListCompetitionRunsCommand(), ExportRun(), CompetitionRunsHistoryCommand())
    }

    /**
     * Helper class that contains all information regarding a [RunManager].
     */
    data class RunSummary(val id: Long, val name: String, val description: String?, val task: String?)

    /**
     * Lists all ongoing competitions runs for the current DRES instance.
     */
    inner class OngoingCompetitionRunsCommand: CliktCommand(name = "ongoing", help = "Lists all ongoing competition runs.") {
        override fun run() {
            if (RunExecutor.managers().isEmpty()){
                println("No runs are currently ongoing!")
                return
            }
            RunExecutor.managers().forEach {
                println("${RunSummary(it.runId, it.name, it.competitionDescription.description, it.currentTask?.name)} (${it.status})")
            }
        }
    }

    /**
     * Lists all competition runs (ongoing and past) for the current DRES instance.
     */
    inner class ListCompetitionRunsCommand(): CliktCommand(name = "list", help = "Lists all (ongoing and past) competition runs.") {
        override fun run() {
            this@CompetitionRunCommand.runs.forEach {
                println("${RunSummary(it.id, it.name, it.competitionDescription.description, it.currentTask?.task?.name)}")
            }
        }
    }

    /**
     * Exports a specific competition run as JSON.
     */
    inner class ExportRun: CliktCommand(name = "export", help = "Exports the competition run as JSON.") {
        private val id: Long by option("-i", "--id").long().required()
        private val path: String by option("-o", "--output").required()
        override fun run() {
            val run = this@CompetitionRunCommand.runs[this.id]
            if (run == null) {
                println("Run does not seem to exist.")
                return
            }

            val path = Paths.get(this.path)
            val mapper = ObjectMapper()
            Files.newBufferedWriter(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
                mapper.writeValue(it, run)
            }
            println("Successfully wrote run ${run.id} to $path.")
        }
    }


    inner class CompetitionRunsHistoryCommand: CliktCommand(name = "history", help = "Lists past Competition Runs") {


        override fun run() {

            this@CompetitionRunCommand.runs.forEach {

                println(it.name)

                println("Teams:")
                it.competitionDescription.teams.forEach {
                    println(it)
                }

                println()
                println("All Tasks:")
                it.competitionDescription.tasks.forEach {
                    println(it)
                }

                println()
                println("Evaluated Tasks:")
                it.runs.forEach {
                    println(it.data.task)

                    println("Submissions")
                    it.data.submissions.forEach {
                        println(it)
                    }
                }
                println()
            }

        }

    }

}