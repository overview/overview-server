/**
 * DocumentSetIndexer.scala
 * Given a list of DoucmentAtURL obejcts, retrieve the text of each, index, build tree, add to the DB
 *
 * Overview Project, created August 2012
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import java.sql.Connection
import org.squeryl.PrimitiveTypeMode.using
import org.squeryl.Session
import org.overviewproject.database.{ Database, DB }
import org.overviewproject.persistence.{ DocumentWriter, NodeWriter }
import org.overviewproject.postgres.SquerylPostgreSqlAdapter
import org.overviewproject.util.{ DocumentConsumer, Logger }
import org.overviewproject.util.DocumentSetCreationJobStateDescription.{ Clustering, Done, Saving }
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn, makeNestedProgress }



class DocumentSetIndexer(nodeWriter: NodeWriter, progAbort: ProgressAbortFn) extends DocumentConsumer {

  // --- private ---
  val t0 = System.nanoTime()
  private var fractionFetched = 0
  private val fetchingFraction = 0.5 // what percent done do we say when we're all done fetching docs?
  private val savingFraction = 0.98

  private val vectorGen = new DocumentVectorGeneratorWithBigrams

  // When we get the document text back, we add the document to the database and feed the text to the vector generator
  def processDocument(documentId: Long, text: String): Unit = {
    vectorGen.addDocument(documentId, Lexer.makeTerms(text))
  }

  private def addDocumentDescriptions(docTree: DocTreeNode)(implicit c: Connection) {
    if (docTree.docs.size == 1 && docTree.description != "") Database.inTransaction {
      DocumentWriter.updateDescription(docTree.docs.head, docTree.description)
    }
    else docTree.children.foreach(addDocumentDescriptions)
  }

  def productionComplete() {
    Logger.logElapsedTime("Retrieved " + vectorGen.numDocs, t0)

    if (!progAbort(Progress(fetchingFraction, Clustering))) {
      val t1 = System.nanoTime()
      val docVecs = vectorGen.documentVectors()
      val docTree = BuildDocTree(docVecs, makeNestedProgress(progAbort, fetchingFraction, savingFraction))
      DB.withConnection { implicit connection => addDocumentDescriptions(docTree) }

      Logger.logElapsedTime("Clustered documents", t1)

      // Save tree to database
      if (!progAbort(Progress(savingFraction, Saving))) {
        val t2 = System.nanoTime()
        Database.inTransaction {
          implicit val connection = Database.currentConnection
          nodeWriter.write(docTree)
        }
        Logger.logElapsedTime("Saved DocumentSet to DB", t2)

        progAbort(Progress(1, Done))
      }
    }
  }
}
