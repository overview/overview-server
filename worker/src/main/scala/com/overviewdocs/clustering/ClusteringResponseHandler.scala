package com.overviewdocs.clustering

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.tables.{DanglingNodes,Trees}
import com.overviewdocs.persistence.{DocumentUpdater,NodeWriter}

/** Modifies the database according to the output of Main.scala.
  *
  * We call `onProgress` repeatedly, with a fraction (`0.0` to `1.0`) and a
  * translatable String (key and arguments separated from commas). Possible
  * arguments:
  *
  * * `onProgress(0.2, 'clustering')`
  * * `onProgress(0.95, 'saving')`
  *
  * See `Main.scala` (in this directory) to understand the input this class
  * expects.
  *
  * @param treeId Tree ID (used to calculate node IDs and report progress).
  * @param onProgress Function to call when we have progress updates.
  */
class ClusteringResponseHandler(treeId: Long, onProgress: (Double, String) => Unit) extends HasBlockingDatabase {
  private sealed trait State
  private final case object Progress extends State
  private final case class Documents(nTotal: Int) extends State
  private final case class Nodes(nTotal: Int) extends State

  private val ProgressRegex = """([01]\.\d+)""".r
  private val NDocumentsRegex = """(\d+) DOCUMENTS""".r
  private val DocumentRegex = """(\d+),(.*)""".r
  private val NNodesRegex = """(\d+) NODES""".r
  private val NodeRegex = """(\d+),(\d*),([tf]),([^,]*),([\d ]+)""".r

  private var state: State = Progress
  private val rootNodeId: Long = (treeId & 0xffffffff00000000L) | ((treeId & 0xfff) << 20L)
  private val documentUpdater = new DocumentUpdater
  private val nodeWriter = new NodeWriter
  private var nDocumentsWritten: Int = 0
  private var nNodesWritten: Int = 0
  private var nDocumentsPerProgress: Int = -1
  private var nNodesPerProgress: Int = -1

  private def onClusterProgress(fraction: Double): Unit = {
    onProgress(0.9 * fraction, "clustering")
  }

  private def onDocumentsProgress(nWritten: Int, nTotal: Int): Unit = {
    onProgress(0.9 + 0.02 * nWritten / nTotal, "saving")
  }

  private def onNodesProgress(nWritten: Int, nTotal: Int): Unit = {
    onProgress(0.92 + 0.08 * nWritten / nTotal, "saving")
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

  /** Handle the next line of output from Main.scala.
    */
  def onLine(line: String): Unit = {
    (state, line) match {
      case (Progress, ProgressRegex(fractionString)) => {
        onClusterProgress(fractionString.toDouble)
      }

      case (Progress, NDocumentsRegex(nDocumentsString)) => {
        val nTotal = nDocumentsString.toInt
        state = Documents(nTotal)
        nDocumentsPerProgress = math.max(1, nTotal / 10)
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
        nNodesPerProgress = math.max(1, nTotal / 10)
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

  /** Handle the fact that there are no more lines from Main.scala.
    */
  def finish: Unit ={
    nodeWriter.blockingFlush

    state match {
      case Nodes(nTotal) if nNodesWritten == nTotal => finalizeTree
      case _ => {} // there was an error; things didn't finish
    }
  }
}
