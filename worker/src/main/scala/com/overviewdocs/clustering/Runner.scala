package com.overviewdocs.clustering

import java.io.{BufferedReader,IOException,InputStreamReader,PrintWriter,OutputStreamWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.Tree
import com.overviewdocs.models.tables.Trees
import com.overviewdocs.util.{Configuration,Logger,JavaCommand}

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

  private val logger = Logger.forClass(getClass)

  private lazy val updateQuery = {
    Trees
      .filter(_.id === tree.id)
      .map(t => (t.progress, t.progressDescription))
  }

  private def reportSuccess: Unit = {
    logger.info("Tree {}: success", tree.id)
    blockingDatabase.runUnit(updateQuery.update((1.0, "success")))
  }

  private def reportError: Unit = {
    logger.info("Tree {}: error", tree.id)
    blockingDatabase.runUnit(updateQuery.update((1.0, "error")))
  }

  private def reportTooFewDocuments: Unit = {
    logger.info("Tree {}: not enough documents", tree.id)
    blockingDatabase.runUnit(updateQuery.update((1.0, "notEnoughDocuments")))
  }

  /** Writes progress to the `tree` table.
    *
    * Returns false iff the tree has been deleted.
    */
  private def reportInputProgress(nWritten: Int, nTotal: Int): Boolean = {
    blockingDatabase.run(updateQuery.update((0.3 * nWritten / nTotal), "reading")) == 1
  }

  private def feedDocumentsToChild(documents: CatDocuments, child: Process): Unit = {
    val nTotal: Int = documents.length
    val nPerProgress: Int = math.max(1, nTotal / 30)
    var nWritten: Int = 0

    try {
      val stdin = child.getOutputStream()
      val stdinWriter = new PrintWriter(new OutputStreamWriter(stdin, StandardCharsets.UTF_8), true)

      documents.foreach { document =>
        stdinWriter.println(s"${document.id}\t${document.tokens.filter(_.length < maxTokenLength).mkString(" ")}")
        nWritten += 1
        if (nWritten % nPerProgress == 0) reportInputProgress(nWritten, nTotal)
      }
      stdinWriter.close
    } catch {
      case _: IOException => {
        // The child process stopped listening -- i.e., it died.
        // That means it'll return an exit code other than zero, so we don't
        // need to do anything special.
      }
    }
  }

  private def handleOutputFromChild(child: Process): Unit = {
    def onClusterProgress(fraction: Double, message: String): Unit = {
      if (blockingDatabase.run(updateQuery.update((0.3 + fraction * 0.69, message))) == 0) {
        // The tree table entry is gone. To the user, this means "cancel". To
        // us, it means this process is now chugging for nothing.
        child.destroyForcibly
      }
    }

    try {
      val stdout = child.getInputStream()
      val stdoutReader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8))

      val clusteringResponseHandler = new ClusteringResponseHandler(tree.id, onClusterProgress)

      def step: Boolean = {
        Option(stdoutReader.readLine) match {
          case None => false
          case Some(line) => clusteringResponseHandler.onLine(line); true
        }
      }

      while (step) {}

      stdoutReader.close
      clusteringResponseHandler.finish
    } catch {
      case _: IOException => {
        // The stream was closed by something else (i.e., the child). We don't
        // much care what happens, because we assume the child's exit code will
        // be non-zero.
      }
    }
  }

  def runBlocking: Unit = {
    logger.info("Processing tree {}", tree)
    val documents = new CatDocuments(tree.documentSetId, tree.tagId)

    if (documents.length < 2) {
      reportTooFewDocuments
      return
    }

    val process: Process = new ProcessBuilder(command: _*)
      .redirectError(ProcessBuilder.Redirect.INHERIT) // Print errors on stderr. Logstash will email us.
      .start()

    feedDocumentsToChild(documents, process)
    handleOutputFromChild(process)

    process.waitFor match {
      case 0 => reportSuccess
      case _ => reportError // if tree was deleted, this error vanishes
    }
  }

  private val maxTokenLength: Int = Configuration.getInt("max_clustering_token_length")

  private def command: Seq[String] = JavaCommand(
    "-Xmx" + Configuration.getString("clustering_memory"),
    "com.overviewdocs.clustering.Main",
    tree.lang, tree.suppliedStopWords, tree.importantWords
  )
}
