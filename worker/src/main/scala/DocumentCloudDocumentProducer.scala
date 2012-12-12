/*
 * DocumentCloudDocumentProducer.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package overview.http

import akka.actor._
import akka.dispatch.{ Future, Promise, Await }
import akka.util.Timeout
import org.overviewproject.clustering.DCDocumentAtURL
import org.overviewproject.clustering.DocumentSetIndexer
import org.overviewproject.database.Database;
import overview.util.{ DocumentConsumer, DocumentProducer, Logger, WorkerActorSystem }
import overview.util.Progress._
import overview.util.DocumentSetCreationJobStateDescription._
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.DocumentType._
import persistence.DocumentWriter

/** Feeds the documents from sourceDocList to the consumer */
class DocumentCloudDocumentProducer(documentSetId: Long, sourceDocList: Traversable[DCDocumentAtURL], consumer: DocumentConsumer,
  progAbort: ProgressAbortFn)  extends DocumentProducer {

  private val FetchingFraction = 0.9
  private var numDocs = 0
  
  def produce() {
    val t0 = System.nanoTime()

    // Retrieve all that stuff!

    WorkerActorSystem.withActorSystem { implicit context =>
      val bulkHttpRetriever = new BulkHttpRetriever[DCDocumentAtURL](new AsyncHttpRequest)
      val retrievalDone = bulkHttpRetriever.retrieve(sourceDocList, notify)

      // Now, wait on this thread until all docs are in
      val docsNotFetched = Await.result(retrievalDone, Timeout.never.duration)
      Logger.info("Failed to retrieve " + docsNotFetched.length + " documents")
    }

    consumer.productionComplete()
  }

  private def notify(doc: DCDocumentAtURL, text: String) {
    val id =  Database.inTransaction{
      val document = Document(DocumentCloudDocument, documentSetId, doc.title, documentcloudId = Some(doc.documentCloudId))
      DocumentWriter.write(document)
      document.id
    }
    consumer.processDocument(id, text)
    numDocs += 1
    progAbort(
      Progress(numDocs * FetchingFraction / sourceDocList.size, Retrieving(numDocs, sourceDocList.size)))
  }
}