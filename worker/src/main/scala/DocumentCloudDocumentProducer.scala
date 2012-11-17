package overview.http

import akka.actor._
import akka.dispatch.{ Future, Promise, Await }
import akka.util.Timeout
import overview.clustering.{ DCDocumentAtURL, DocumentSetIndexer }
import overview.util.{ DocumentConsumer, DocumentProducer, Logger, WorkerActorSystem }
import overview.util.Progress._
import overview.util.DocumentSetCreationJobStateDescription._
import database.DB

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
    val id =  DB.withConnection { implicit connection =>
      doc.write(documentSetId)
    }
    consumer.processDocument(id, text)
    numDocs += 1
    progAbort(
      Progress(numDocs * FetchingFraction / sourceDocList.size, Retrieving(numDocs, sourceDocList.size)))
  }
}