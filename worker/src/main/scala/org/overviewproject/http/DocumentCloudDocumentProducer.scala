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
import org.overviewproject.documentcloud.{ Document => RetrievedDocument, QueryProcessor }
import org.overviewproject.documentcloud.DocumentRetrievalError
import org.overviewproject.documentcloud.DocumentRetriever
import org.overviewproject.documentcloud.QueryInformation
import org.overviewproject.documentcloud.QueryProcessorProtocol.Start

/** Feeds the documents from sourceDocList to the consumer */
class DocumentCloudDocumentProducer(documentSetId: Long, query: String, credentials: Option[Credentials], maxDocuments: Int, consumer: DocumentConsumer,
  progAbort: ProgressAbortFn) extends DocumentProducer with PersistentDocumentSet {

  private val MaxInFlightRequests = 4
  private val RequestQueueName = "requestqueue"
  private val QueryProcessorName = "queryprocessor"

  private val FetchingFraction = 0.5
  private val ids = new DocumentSetIdGenerator(documentSetId)
  private var numDocs = 0
  private var queryInformation: QueryInformation = _
  private var totalDocs: Option[Int] = None

  def produce() {
    val t0 = System.nanoTime()

    WorkerActorSystem.withActorSystem { implicit context =>

      queryInformation = new QueryInformation
      val asyncHttpClient = new AsyncHttpClientWrapper
      val requestQueue = context.actorOf(Props(new RequestQueue(asyncHttpClient, MaxInFlightRequests)), RequestQueueName)
      def retrieverGenerator(document: RetrievedDocument, receiver: ActorRef) = new DocumentRetriever(document, receiver, requestQueue, credentials)

      val queryProcessor = context.actorOf(Props(new QueryProcessor(query, queryInformation, credentials, notify, requestQueue, retrieverGenerator)), QueryProcessorName)

      println(s"Query --> ${queryProcessor.path}  Queue --> ${requestQueue.path}")
      // Now, wait on this thread until all docs are in

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

  private def notify(doc: RetrievedDocument, text: String)(implicit context: ActorSystem): Unit = {
    val id = Database.inTransaction {
      val document = Document(DocumentCloudDocument, documentSetId, id = ids.next, title = Some(doc.title), documentcloudId = Some(doc.id))
      DocumentWriter.write(document)
      document.id
    }
    //throw (new java.lang.OutOfMemoryError("heap space"))
    consumer.processDocument(id, text)
    numDocs += 1

    val isCancelled = progAbort(Progress(numDocs * FetchingFraction / getTotalDocs, Retrieving(numDocs, getTotalDocs)))
    if (isCancelled) shutdownActors
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
