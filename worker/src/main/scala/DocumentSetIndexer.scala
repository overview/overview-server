/**
 * DocumentSetIndexer.scala
 * Given a list of DoucmentAtURL obejcts, retrieve the text of each, index, build tree, add to the DB
 *
 * Overview Project, created August 2012
 *
 * @author Jonathan Stray
 *
 */

package overview.clustering

import java.sql.Connection
import overview.clustering.ClusterTypes._
import overview.http._
import overview.util.Logger
import overview.util.Progress._
import persistence.{ DocumentWriter, NodeWriter }
import database.DB

import akka.actor._
import akka.dispatch.{ Future, Promise, Await }
import akka.util.Timeout

class DocumentSetIndexer(sourceDocList: Traversable[DCDocumentAtURL],
  nodeWriter: NodeWriter,
  documentWriter: DocumentWriter,
  progAbort: ProgressAbortFn) {

  private var fractionFetched = 0
  private val fetchingFraction = 0.9 // what percent done do we say when we're all done fetching docs?
  private val savingFraction = 0.99

  private def logElapsedTime(op: String, t0: Long) {
    val t1 = System.nanoTime()
    Logger.info(op + ", time: " + ("%.2f" format (t1 - t0) / 1e9) + " seconds")
  }

  private val vectorGen = new DocumentVectorGenerator

  // When we get the document text back, we add the document to the database and feed the text to the vector generator
  private def processDocument(doc: DCDocumentAtURL, text: String): Unit = {
    val documentId = DB.withConnection {
      implicit connection => documentWriter.write(doc.title, doc.documentCloudId)
    }
    vectorGen.addDocument(documentId, Lexer.makeTerms(text))
    progAbort(Progress(vectorGen.numDocs * fetchingFraction / sourceDocList.size, "Retrieving documents"))
  }

  private def addDocumentDescriptions(docTree: DocTreeNode)(implicit c: Connection) {
    if (docTree.docs.size == 1 && docTree.description != "") 
      documentWriter.updateDescription(docTree.docs.head, docTree.description)
    else docTree.children.foreach(addDocumentDescriptions)
  }

  def BuildTree(): Unit = {

    val t0 = System.nanoTime()

    // Retrieve all that stuff!
    val retrievalDone = BulkHttpRetriever[DCDocumentAtURL](sourceDocList,
      (doc, text) => processDocument(doc, text))

    // Now, wait on this thread until all docs are in 
    val docsNotFetched = Await.result(retrievalDone, Timeout.never.duration)
    logElapsedTime("Retrieved" + vectorGen.numDocs + " documents, with " + docsNotFetched.length + " not fetched", t0)

    // Cluster (build the tree)
    progAbort(Progress(fetchingFraction, "Clustering documents"))
    val t1 = System.nanoTime()
    val docVecs = vectorGen.documentVectors()
    val docTree = BuildDocTree(docVecs, makeNestedProgress(progAbort, fetchingFraction, savingFraction))
    DB.withConnection { implicit connection =>  addDocumentDescriptions(docTree) }

    logElapsedTime("Clustered documents", t1)

    // Save tree to database
    progAbort(Progress(savingFraction, "Saving document tree"))
    val t2 = System.nanoTime()
    DB.withTransaction { implicit connection =>
      nodeWriter.write(docTree)
    }
    logElapsedTime("Saved DocumentSet to DB", t2)

    progAbort(Progress(1, "Done"))
  }
}
