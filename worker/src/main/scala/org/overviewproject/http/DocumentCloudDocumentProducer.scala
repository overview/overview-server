/*
 * DocumentCloudDocumentProducer.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.http

import akka.actor._
import scala.concurrent.{ Future, Promise, Await }
import scala.concurrent.duration.Duration

import org.overviewproject.clustering.{ DCDocumentAtURL, DocumentCloudSource, DocumentSetIndexer }
import org.overviewproject.database.Database
import org.overviewproject.persistence.{ DocRetrievalErrorWriter, DocumentSetIdGenerator, DocumentWriter, PersistentDocumentSet }
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.DocumentType._
import org.overviewproject.util.{ DocumentConsumer, DocumentProducer, Logger, WorkerActorSystem }
import org.overviewproject.util.DocumentSetCreationJobStateDescription._
import org.overviewproject.util.Progress._


/** Feeds the documents from sourceDocList to the consumer */
class DocumentCloudDocumentProducer(documentSetId: Long, sourceDocList: DocumentCloudSource, consumer: DocumentConsumer,
  progAbort: ProgressAbortFn) extends DocumentProducer with PersistentDocumentSet {

  private val FetchingFraction = 0.5
  private var numDocs = 0
  private val ids = new DocumentSetIdGenerator(documentSetId)
  
  def produce() {
    val t0 = System.nanoTime()

    // Retrieve all that stuff!

    WorkerActorSystem.withActorSystem { implicit context =>
      val bulkHttpRetriever = new DocumentCloudBulkHttpRetriever(new AsyncHttpRequest, new NonRedirectingHttpRequest)
      val retrievalDone = bulkHttpRetriever.retrieve(sourceDocList, notify)

      // Now, wait on this thread until all docs are in
      val docsNotFetched = Await.result(retrievalDone.future, Duration.Inf)
      Logger.info("Failed to retrieve " + docsNotFetched.length + " documents")
      Database.inTransaction {
        DocRetrievalErrorWriter.write(documentSetId, docsNotFetched)
      }
    }

    consumer.productionComplete()
    val overflowCount = scala.math.max(0, sourceDocList.totalDocumentsInQuery - sourceDocList.size)
    updateDocumentSetCounts(documentSetId, numDocs, overflowCount)
  }

  private def notify(doc: DCDocumentAtURL, text: String): Boolean = {
    val id = Database.inTransaction {
      val document = Document(DocumentCloudDocument, documentSetId, id = ids.next, title = Some(doc.title), documentcloudId = Some(doc.documentCloudId))
      DocumentWriter.write(document)
      document.id
    }

    consumer.processDocument(id, text)
    numDocs += 1
    !progAbort(
      Progress(numDocs * FetchingFraction / sourceDocList.size, Retrieving(numDocs, sourceDocList.size)))
  }
}
