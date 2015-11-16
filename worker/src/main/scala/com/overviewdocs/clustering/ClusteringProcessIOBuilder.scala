package com.overviewdocs.clustering

import java.io.{IOException,InputStream,OutputStream,OutputStreamWriter}
import java.nio.charset.StandardCharsets
import scala.sys.process.ProcessIO

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.tables.{DanglingNodes,Trees}
import com.overviewdocs.persistence.{DocumentUpdater,NodeWriter}

/** Feeds data to the attached process and feeds its output to the database.
  *
  * Usage:
  *
  *     val builder = new ProcessIOBuilder(1L, None, 1000L, _ => ())
  *     val process = Process("java ...").run(builder.toProcessIO)
  *     val retval = process.exitValue
  *
  * Scala will create three threads for the child process: one for stdin, one
  * for stdout, and one for stderr. It will feed `CatDocuments` to stdin, and
  * it will feed stdout to `DocumentUpdater` and `NodeWriter`. It will also
  * update `tree.document_count`.
  *
  * We call `onProgress` repeatedly, with a fraction (`0.0` to `1.0`) and a
  * translatable String (key and arguments separated from commas). Possible
  * arguments:
  *
  * * `onProgress(0.1, 'reading')`
  * * `onProgress(0.6, 'clustering')`
  * * `onProgress(0.9, 'saving')`
  *
  * See `Main.scala` (in this directory) to understand the format this class
  * expects.
  *
  * The child process should not output anything on its stderr. If it does, we
  * pipe the message to *this* process's stderr.
  *
  * Remember: ProcessIO has no concept of error. Use the corresponding Process
  * for that logic.
  *
  * @param treeId Tree ID (used to calculate node IDs and report progress).
  * @param catDocuments Source documents to cluster.
  * @param onProgress Function to call when we have progress updates.
  */
class ClusteringProcessIOBuilder(
  treeId: Long,
  documents: CatDocuments,
  onProgress: (Double, String) => Unit
) extends HasBlockingDatabase {
  private sealed trait InputState
  private final case object Progress extends InputState
  private final case class Documents(nTotal: Int) extends InputState
  private final case class Nodes(nTotal: Int) extends InputState

  private val ProgressRegex = """([01]\.\d+)""".r
  private val NDocumentsRegex = """(\d+) DOCUMENTS""".r
  private val DocumentRegex = """(\d+),(.*)""".r
  private val NNodesRegex = """(\d+) NODES""".r
  private val NodeRegex = """(\d+),(\d*),([tf]),([^,]*),([\d ]+)""".r

  private val rootNodeId: Long = (treeId & 0xffffffff00000000L) | ((treeId & 0xfff) << 20L)

  private def onInputProgress(nWritten: Int, nTotal: Int): Unit = {
    onProgress(0.4 * nWritten / nTotal, "reading")
  }

  private def onClusterProgress(fraction: Double): Unit = {
    onProgress(0.4 + 0.5 * fraction, "clustering")
  }

  private def onDocumentsProgress(nWritten: Int, nTotal: Int): Unit = {
    onProgress(0.9 + 0.02 * nWritten / nTotal, "saving")
  }

  private def onNodesProgress(nWritten: Int, nTotal: Int): Unit = {
    onProgress(0.92 + 0.07 * nWritten / nTotal, "saving") // max 0.99: 1.0 is special
  }

  /** Add a reference to DanglingNodes.
    *
    * The node doesn't have to exist when this method is called.
    */
  private def addDanglingNode: Unit = {
    import database.api._
    blockingDatabase.runUnit(DanglingNodes.map(_.rootNodeId).+=(rootNodeId))
  }

  /** Move the root node ID from DanglingNodes to Trees. */
  private def finalizeTree: Unit = {
    import database.api._
    import database.executionContext
    blockingDatabase.runUnit((for {
      _ <- DanglingNodes.filter(_.rootNodeId === rootNodeId).delete
      _ <- Trees.filter(_.id === treeId).map(_.rootNodeId).update(Some(rootNodeId))
    } yield ()).transactionally)
  }

  /** Update Tree.documentCount. */
  private def writeNDocuments(nDocuments: Int): Unit = {
    import database.api._

    blockingDatabase.runUnit(Trees.filter(_.id === treeId).map(_.documentCount).update(Some(nDocuments)))
  }

  private def withStdin(stream: OutputStream): Unit = {
    var n = 0
    val nTotal: Int = documents.length
    val nPerProgress: Int = math.max(1, nTotal / 100)
    val writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)

    onInputProgress(0, nTotal)

    documents.foreach { document =>
      writer.write(s"${document.id},${document.tokens.mkString(" ")}\n")
      n += 1
      if (n % nPerProgress == 0) onInputProgress(n, nTotal)
    }

    writer.close
    stream.close
  }

  private def withStdout(stream: InputStream): Unit = {
    val documentUpdater = new DocumentUpdater
    val nodeWriter = new NodeWriter
    var state: InputState = Progress
    var nDocumentsWritten: Int = 0
    var nNodesWritten: Int = 0
    var nDocumentsPerProgress: Int = -1
    var nNodesPerProgress: Int = -1

    io.Source.fromInputStream(stream)(io.Codec.UTF8).getLines.foreach { line =>
      (state, line) match {
        case (Progress, ProgressRegex(fractionString)) => {
          onClusterProgress(fractionString.toDouble)
        }

        case (Progress, NDocumentsRegex(nDocumentsString)) => {
          val nTotal = nDocumentsString.toInt
          state = Documents(nTotal)
          nDocumentsPerProgress = math.max(1, nTotal / 20)
          writeNDocuments(nTotal)
          onDocumentsProgress(0, nTotal)
        }

        case (Documents(nTotal), DocumentRegex(idString, keywordsString)) => {
          documentUpdater.blockingUpdateKeywordsAndFlushIfNeeded(idString.toLong, keywordsString.split(' '))
          nDocumentsWritten += 1
          if (nDocumentsWritten % nDocumentsPerProgress == 0) onDocumentsProgress(nDocumentsWritten, nTotal)
        }

        case (Documents(_), NNodesRegex(nNodesString)) => {
          documentUpdater.blockingFlush

          addDanglingNode

          val nTotal = nNodesString.toInt
          state = Nodes(nTotal)
          nNodesPerProgress = math.max(1, nTotal)
          onNodesProgress(0, nTotal)
        }

        case (Nodes(nTotal), NodeRegex(idString, parentIdString, isLeafString, description, documentIdsString)) => {
          val id: Long = idString.toLong | rootNodeId
          val parentId: Option[Long] = parentIdString match {
            case "" => None
            case s => Some(s.toLong | rootNodeId)
          }
          val isLeaf: Boolean = isLeafString == "t"
          val documentIds: Seq[Long] = documentIdsString.split(' ').filter(_.nonEmpty).map(_.toLong)

          nodeWriter.blockingCreateAndFlushIfNeeded(id, rootNodeId, parentId, description, isLeaf, documentIds)

          nNodesWritten += 1
          if (nNodesWritten % nNodesPerProgress == 0) onNodesProgress(nNodesWritten, nTotal)
        }

        // crash with MatchError on unexpected output
      }
    }

    nodeWriter.blockingFlush

    state match {
      case Nodes(nTotal) if nNodesWritten == nTotal => finalizeTree
      case _ => {} // there was an error; things didn't finish
    }

    stream.close
  }

  private def withStderr(stream: InputStream): Unit = {
    val buf = new Array[Byte](10 * 1024) // arbitrary size
    while (true) {
      val nBytes = stream.read(buf)
      if (nBytes == -1) {
        stream.close
        return
      }
      System.err.write(buf, 0, nBytes)
    }
  }

  private def ignoreInterrupt[A](f: A => Unit): A => Unit = { arg =>
    try {
      f(arg)
    } catch {
      case _: InterruptedException => {
        /*
         * Scala process.destroy() interrupts all threads. Our code is designed
         * such that cancellation at any time leaves things consistent.
         * Therefore, an interrupt is completely useless ... but we can
         * probably ignore. (Assume Slick handles InterruptedException cleanly.)
         */
      }
      case _: IOException => {
        /*
         * Multithreading garbage: BufferedInputStream.read() can throw
         * "IOException: stream closed" if the stream is opened in another
         * thread.
         *
         * Again, this is an annoying exception that originates from Scala's
         * sys.process package.
         */
      }
    }
  }

  def toProcessIO: ProcessIO = new ProcessIO(
    ignoreInterrupt(withStdin),
    ignoreInterrupt(withStdout),
    ignoreInterrupt(withStderr)
  )
}
