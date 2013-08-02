/*
 * DocumentCloudDocumentProducer.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.http

import scala.language.postfixOps
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

import org.overviewproject.database.Database
import org.overviewproject.documentcloud.{Document => RetrievedDocument, _ }
import org.overviewproject.documentcloud.ImporterProtocol._
import org.overviewproject.persistence._
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.DocumentSetCreationJobState.Cancelled
import org.overviewproject.tree.orm.DocumentType.DocumentCloudDocument
import org.overviewproject.util._
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Retrieving
import org.overviewproject.util.Progress.{Progress, ProgressAbortFn}

import akka.actor._

/** Feeds the documents from sourceDocList to the consumer */
class DocumentCloudDocumentProducer(job: PersistentDocumentSetCreationJob, query: String, credentials: Option[Credentials], maxDocuments: Int, consumer: DocumentConsumer,
  progAbort: ProgressAbortFn) extends DocumentProducer with PersistentDocumentSet {

  private val MaxInFlightRequests = Configuration.maxInFlightRequests
  private val SuperTimeout = 6 minutes // Regular timeout is 5 minutes
  private val RequestQueueName = "requestqueue"
  private val QueryProcessorName = "queryprocessor"
  private val ImporterName = "importer"

  private val FetchingFraction = 0.5
  private val documentSetId = job.documentSetId
  private val indexingSession = SearchIndex.startDocumentSetIndexingSession(documentSetId)  
  private val ids = new DocumentSetIdGenerator(documentSetId)
  private var numDocs = 0
  private var totalDocs: Option[Int] = None

  def produce() {
    val t0 = System.nanoTime()

    // First step to partitioning work into actors.
    // Next step is to create supervisor actor that manages the actors being created below.
    // A separate actor could monitor cancellation status an then inform the supervisor actor
    // which would shut down everything. For now, it's a bit messy.

    // The following actors and components are setup:
    // queryProcessor - The main object that drives the query. Requests query result pages
    //   and spawns DocumentRetrievers for each document. The retrieval results are sent
    //   to a DocumentReceiver that processes each document with a callback function.
    // requestQueue - an actor that manages the incoming requests
    // asyncHttpClient - A wrapper around AsyncHttpClient
    // retrieverGenerator - A factory for actors that will retrieve documents. One actor is 
    //   responsible for one document only. DocumentRetrievers simply retrieve the document. 
    //   A different retriever could be used to request the document text page-by-page.

    var result: RetrievalResult = null
    
    WorkerActorSystem.withActorSystem { implicit context =>

      val importResult = Promise[RetrievalResult]
      val asyncHttpClient = new AsyncHttpClientWrapper
      val requestQueue = context.actorOf(Props(new RequestQueue(asyncHttpClient, MaxInFlightRequests, SuperTimeout)), RequestQueueName)
      def retrieverCreator(document: RetrievedDocument, receiver: ActorRef) =
        new DocumentRetriever(document, receiver, requestQueue, credentials, RequestRetryTimes())

      def splitterCreator(document: RetrievedDocument, receiver: ActorRef) =
        new DocumentSplitter(document, receiver, retrieverCreator)

      val retrieverGenerator = if (job.splitDocuments) {
        val retrieverFactory = new RetrieverFactory {
          def produce(document: RetrievedDocument, receiver: ActorRef): Actor = splitterCreator(document, receiver)
        }

        new DocumentPageRetrieverGenerator(retrieverFactory, maxDocuments)
      }
      else {
        val retrieverFactory = new RetrieverFactory {
          def produce(document: RetrievedDocument, receiver: ActorRef): Actor = retrieverCreator(document, receiver)
        }
        
        new DocumentRetrieverGenerator(retrieverFactory, maxDocuments)
      }

      val importer = context.actorOf(Props(
        new Importer(query, credentials,
          retrieverGenerator, notify, maxDocuments,
          updateRetrievalProgress,
          importResult)), ImporterName)

      try {
        importer ! StartImport()
        result = Await.result(importResult.future, Duration.Inf)
        Logger.info("Failed to retrieve " + result.failedRetrievals.length + " documents")
        Database.inTransaction {
          DocRetrievalErrorWriter.write(documentSetId, result.failedRetrievals)
        }
      } catch {
        case t: Throwable if (t.getCause() != null) => throw t.getCause()
        case t: Throwable => throw t
      } finally {
        shutdownActors
      }
    }

    indexingSession.complete
    consumer.productionComplete()
    Await.result(indexingSession.requestsComplete, Duration.Inf)
    Logger.info("Indexing complete")
    
    val overflowCount = result.totalDocumentsInQuery - result.numberOfDocumentsRetrieved
    updateDocumentSetCounts(documentSetId, numDocs, overflowCount)
  }

  private def updateRetrievalProgress(retrieved: Int, total: Int): Unit =
    progAbort(Progress(retrieved * FetchingFraction / total, Retrieving(retrieved, total)))

  private def notify(doc: RetrievedDocument, text: String)(implicit context: ActorSystem): Unit = {
    val id = Database.inTransaction {
      val document = Document(DocumentCloudDocument, documentSetId, id = ids.next, title = Some(doc.title), documentcloudId = Some(doc.id))
      DocumentWriter.write(document)
      document.id
    }
    
    indexingSession.indexDocument(documentSetId, id, text, Some(doc.title), Some(doc.id))

    consumer.processDocument(id, text)
    numDocs += 1

    if (job.state == Cancelled) shutdownActors
  }

  private def shutdownActors(implicit context: ActorSystem): Unit = {
    val importerActor: ActorRef = context.actorFor(s"user/$ImporterName")

    context.stop(importerActor)
  }

  
}
