package com.overviewdocs.clustering

import java.nio.file.Paths
import scala.concurrent.{ExecutionContext,Future,blocking}
import scala.sys.process.Process

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.Tree
import com.overviewdocs.models.tables.Trees

/** Runs Main.scala and inserts the results into the database.
  *
  * When the `run()` method returns, the Tree it refers to will be completed:
  * the `tree` in the database will either have nodes or an appropriate error
  * message. And the `run()` method is guaranteed to return, even if the
  * child process crashes.
  *
  * Progress reports go straight to the database; at the same time, we'll poll
  * for `tree.cancelled`. If the tree is cancelled, we kill the child process.
  *
  * The tree is written by writing to the `node` table and then setting
  * `tree.root_node_id` as the final operation. If this process is interrupted,
  * the root ID of the written nodes will be stored in the `dangling_node`
  * table.
  */
class Runner(val tree: Tree) extends HasBlockingDatabase {
  import database.api._

  private def updateQuery = {
    Trees
      .filter(t => t.id === tree.id && !t.cancelled)
      .map(t => (t.progress, t.progressDescription))
  }

  private def reportSuccess: Unit = {
    blockingDatabase.runUnit(updateQuery.update((1.0, "success")))
  }

  private def reportError: Unit = {
    blockingDatabase.runUnit(updateQuery.update((1.0, "error")))
  }

  def runBlocking: Unit = {
    var hackyMaybeProcess: Option[Process] = None
    val cancelled: Boolean = false

    def onProgress(fraction: Double, message: String): Unit = {
      if (blockingDatabase.run(sqlu"""
        UPDATE tree
        SET progress = $fraction, progress_description = $message
        WHERE id = ${tree.id}
          AND cancelled = FALSE
      """) == 0) {
        // Either the tree was deleted or cancelled == true. Either way, this
        // clustering job cannot produce any value, so we should kill it if it
        // hasn't crashed already.
        hackyMaybeProcess.foreach(_.destroy)
      }
    }

    val processIO = new ClusteringProcessIOBuilder(tree.documentSetId, tree.tagId, tree.id, onProgress).toProcessIO
    val process: Process = Process(command).run(processIO)
    hackyMaybeProcess = Some(process)

    process.exitValue match {
      case 0 => reportSuccess
      case _ => reportError
    }
  }

  private def command: Seq[String] = Seq(
    Runner.javaPath,
    "-cp", Runner.classPath,
    "com.overviewdocs.clustering.Main",
    tree.lang, tree.suppliedStopWords, tree.importantWords
  )
}

object Runner {
  private val javaPath: String = Paths.get(System.getProperty("java.home")).resolve("bin").resolve("java").toString
  private val classPath: String = System.getProperty("java.class.path")
}
