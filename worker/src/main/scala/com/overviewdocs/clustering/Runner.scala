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
  * to see whether the tree was deleted. If the tree is missing, we kill the
  * child process.
  *
  * The tree is written by writing to the `node` table and then setting
  * `tree.root_node_id` as the final operation. If this process is interrupted,
  * the root ID of the written nodes will be stored in the `dangling_node`
  * table.
  */
class Runner(val tree: Tree) extends HasBlockingDatabase {
  import database.api._

  private lazy val updateQuery = {
    Trees
      .filter(_.id === tree.id)
      .map(t => (t.progress, t.progressDescription))
  }

  private def reportSuccess: Unit = {
    blockingDatabase.runUnit(updateQuery.update((1.0, "success")))
  }

  private def reportError: Unit = {
    blockingDatabase.runUnit(updateQuery.update((1.0, "error")))
  }

  /** Writes progress to the `tree` table.
    *
    * Returns false iff the tree has been deleted.
    */
  private def reportProgress(fraction: Double, message: String): Boolean = {
    blockingDatabase.run(updateQuery.update((fraction, message))) == 1
  }

  def runBlocking: Unit = {
    var hackyMaybeProcess: Option[Process] = None
    val cancelled: Boolean = false

    def onProgress(fraction: Double, message: String): Unit = {
      if (!reportProgress(fraction, message)) {
        // The tree was deleted. This clustering job cannot create any value, so
        // we should kill it ASAP.
        hackyMaybeProcess.foreach(_.destroy)
      }
    }

    val processIO = new ClusteringProcessIOBuilder(tree.documentSetId, tree.tagId, tree.id, onProgress).toProcessIO
    val process: Process = Process(command).run(processIO)
    hackyMaybeProcess = Some(process)

    process.exitValue match {
      case 0 => reportSuccess
      case _ => reportError // if tree was deleted, this error vanishes
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
