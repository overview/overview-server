/*
 * DocumentCloudDocumentProducer.scala
 *
 * Overview
 * Created by Jonas Karlsson, November 2012
 */
package com.overviewdocs.http

import akka.actor._
import java.util.concurrent.TimeoutException
import play.api.libs.json.JsObject
import scala.language.postfixOps
import scala.concurrent.{Await,Future,Promise,blocking}
import scala.concurrent.duration._

import com.overviewdocs.database.DeprecatedDatabase
import com.overviewdocs.documentcloud.{Document => RetrievedDocument, _ }
import com.overviewdocs.documentcloud.ImporterProtocol._
import com.overviewdocs.models.Document
import com.overviewdocs.models.DocumentDisplayMethod
import com.overviewdocs.persistence._
import com.overviewdocs.searchindex.TransportIndexClient
import com.overviewdocs.tree.orm.DocumentSetCreationJobState.Cancelled
import com.overviewdocs.util.{BulkDocumentWriter,Configuration,DocumentProducer,Logger,WorkerActorSystem}
import com.overviewdocs.util.DocumentSetCreationJobStateDescription.Retrieving
import com.overviewdocs.util.Progress.{Progress, ProgressAbortFn}

/** Feeds the documents from sourceDocList to the consumer */
class DocumentCloudDocumentProducer(job: PersistentDocumentSetCreationJob, query: String, credentials: Option[Credentials], maxDocuments: Int,
  progAbort: ProgressAbortFn) extends DocumentProducer with PersistentDocumentSet {

  private val logger: Logger = Logger.forClass(this.getClass)

  private val MaxInFlightRequests = Configuration.getInt("max_inflight_requests")
  private val DocumentCloudUrl = Configuration.getString("documentcloud_url")
  private val SuperTimeout = 6 minutes // Regular timeout is 5 minutes
  private val IndexingTimeout = 3 minutes // Indexing should be complete after clustering is done
  private val RequestQueueName = "requestqueue"
  private val QueryProcessorName = "queryprocessor"
  private val ImporterName = "importer"

  private val FetchingFraction = 1.0
  private val documentSetId = job.documentSetId
  private val ids = new DocumentSetIdGenerator(documentSetId)
  private var numDocs = 0
  private var totalDocs: Option[Int] = None
  private var importer: ActorRef = _

  private def await[A](f: Future[A]): A = {
    scala.concurrent.Await.result(f, scala.concurrent.duration.Duration.Inf)
  }

  override def produce() = {
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
    val bulkWriter = BulkDocumentWriter.forDatabaseAndSearchIndex
    blocking(await(TransportIndexClient.singleton.addDocumentSet(documentSetId)))

    var result: RetrievalResult = null

    def notify(doc: RetrievedDocument, text: String)(implicit context: ActorSystem): Unit = {
      val document = Document(
        ids.next,
        documentSetId,
        Some(s"${DocumentCloudUrl}/documents/${doc.id}"),
        doc.id,
        doc.title,
        doc.pageNumber,
        Seq(),
        new java.util.Date(),
        None,
        None,
        DocumentDisplayMethod.auto,
        JsObject(Seq()),
        text
      )
      blocking(await(bulkWriter.addAndFlushIfNeeded(document)))

      numDocs += 1

      if (job.state == Cancelled) shutdownActors
    }

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

      importer = context.actorOf(Props(
        new Importer(query, credentials,
          retrieverGenerator, notify, maxDocuments,
          updateRetrievalProgress,
          importResult)), ImporterName)

      try {
        importer ! StartImport()
        result = Await.result(importResult.future, Duration.Inf)
        logger.info("Failed to retrieve " + result.failedRetrievals.length + " documents")
        DeprecatedDatabase.inTransaction {
          DocRetrievalErrorWriter.write(documentSetId, result.failedRetrievals)
        }
      } catch {
        case t: Throwable if (t.getCause() != null) => throw t.getCause()
        case t: Throwable => throw t
      } finally {
        shutdownActors
      }
    }

    await(bulkWriter.flush)
    logger.info("Indexing complete")

    val overflowCount = result.totalDocumentsInQuery - result.numberOfDocumentsRetrieved
    updateDocumentSetCounts(documentSetId, numDocs, overflowCount)
    refreshSortedDocumentIds(documentSetId)

    numDocs
  }

  private def updateRetrievalProgress(retrieved: Int, total: Int): Unit = {
    progAbort(Progress(retrieved * FetchingFraction / total, Retrieving(retrieved, total)))
  }

  private def shutdownActors(implicit context: ActorSystem): Unit = {
    context.stop(importer)
  }

}
