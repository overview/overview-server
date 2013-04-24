/*
 * DocumentCloudDocumentProducer.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.http

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import org.overviewproject.database.Database
import org.overviewproject.documentcloud.{Document => RetrievedDocument, DocumentRetriever, QueryInformation, QueryProcessor}
import org.overviewproject.documentcloud.QueryProcessorProtocol.Start
import org.overviewproject.persistence.{DocRetrievalErrorWriter, DocumentSetIdGenerator, DocumentWriter, PersistentDocumentSet}
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.orm.DocumentType.DocumentCloudDocument
import org.overviewproject.util.{DocumentConsumer, DocumentProducer}
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Retrieving
import org.overviewproject.util.{ Logger, WorkerActorSystem }
import org.overviewproject.util.Progress.{Progress, ProgressAbortFn}
import akka.actor._
import org.overviewproject.documentcloud.RequestRetryTimes
import org.overviewproject.util.Configuration
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.persistence.PersistentDocumentSetCreationJob

/** Feeds the documents from sourceDocList to the consumer */
class DocumentCloudDocumentProducer(job: PersistentDocumentSetCreationJob, query: String, credentials: Option[Credentials], maxDocuments: Int, consumer: DocumentConsumer,
  progAbort: ProgressAbortFn) extends DocumentProducer with PersistentDocumentSet {

  private val MaxInFlightRequests = Configuration.maxInFlightRequests
  private val SuperTimeout = 6 minutes // Regular timeout is 5 minutes
  private val RequestQueueName = "requestqueue"
  private val QueryProcessorName = "queryprocessor"

  private val FetchingFraction = 0.5
  private val documentSetId = job.documentSetId
  private val ids = new DocumentSetIdGenerator(documentSetId)
  private var numDocs = 0
  private var queryInformation: QueryInformation = _
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

    WorkerActorSystem.withActorSystem { implicit context =>

      queryInformation = new QueryInformation
      val asyncHttpClient = new AsyncHttpClientWrapper
      val requestQueue = context.actorOf(Props(new RequestQueue(asyncHttpClient, MaxInFlightRequests, SuperTimeout)), RequestQueueName)
      def retrieverGenerator(document: RetrievedDocument, receiver: ActorRef) =
        new DocumentRetriever(document, receiver, requestQueue, credentials, RequestRetryTimes())

      val queryProcessor = context.actorOf(Props(new QueryProcessor(query, queryInformation, credentials, maxDocuments, notify, updateRetrievalProgress, requestQueue, retrieverGenerator)), QueryProcessorName)

      try {
        queryProcessor ! Start()
        val docsNotFetched = Await.result(queryInformation.errors.future, Duration.Inf)
        Logger.info("Failed to retrieve " + docsNotFetched.length + " documents")
        Database.inTransaction {
          DocRetrievalErrorWriter.write(documentSetId, docsNotFetched)
        }
      } catch {
        case t: Throwable if (t.getCause() != null) => throw t.getCause()
        case t: Throwable => throw t
      } finally {
        shutdownActors
      }
    }

    consumer.productionComplete()
    val overflowCount = scala.math.max(0, getTotalDocs - maxDocuments)
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
    consumer.processDocument(id, text)
    numDocs += 1

    if (job.state == Cancelled) shutdownActors
  }

  private def getTotalDocs: Int = totalDocs.getOrElse {
    totalDocs = Some(Await.result(queryInformation.documentsTotal.future, Duration.Inf))
    totalDocs.get
  }

  private def shutdownActors(implicit context: ActorSystem): Unit = {

    val queryProcessorActor: ActorRef = context.actorFor(s"user/$QueryProcessorName")
    val requestQueueActor: ActorRef = context.actorFor(s"user/$RequestQueueName")

    context.stop(requestQueueActor)
    context.stop(queryProcessorActor)
  }

}
