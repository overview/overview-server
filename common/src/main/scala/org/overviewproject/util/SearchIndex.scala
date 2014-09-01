package org.overviewproject.util

import org.elasticsearch.action.bulk.{BulkProcessor,BulkRequest,BulkResponse}
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.Client
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder
import scala.concurrent.{Await,Future,Promise}
import scala.concurrent.duration.Duration

import org.overviewproject.searchindex.NodeIndexClient

/**
 * A session for batch indexing documents. The session starts on creation, and ends after calling `complete`.
 */
trait DocumentSetIndexingSession {
  /** Index the document */
  def indexDocument(documentSetId: Long, id: Long, text: String, title: Option[String], suppliedId: Option[String]): Unit

  /** 
   * Mark the session as complete.
   * Further calls to `indexDocument` will not succeed.  
   */
  def complete: Unit

  /** @returns a [[Future]] that completes when all outstanding index requests have completed */
  def requestsComplete: Future[Unit]
}

/**
 * Interface to the search index
 */
trait SearchIndex {
  def startDocumentSetIndexingSession(documentSetId: Long): DocumentSetIndexingSession
  def deleteDocumentSetAliasAndDocuments(documentSetId: Long): Unit
  
  /** close the client. Any subsequent operations will fail */
  def shutdown: Unit
}


/**
 * Singleton search index client, configured with names of the indeces and aliases we use.
 * When documents are indexed, text, title, and supplied id are included, allowing searches on those fields.
 */
object SearchIndex extends SearchIndex {
  private val ClusterName = Configuration.searchIndex.getString("cluster_name")
  private val Hosts = Configuration.searchIndex.getString("hosts")

  private val indexClient: NodeIndexClient = new NodeIndexClient(ClusterName, Hosts)

  private def await[T](f: Future[T]): T = Await.result(f, Duration.Inf)

  override def shutdown = indexClient.close

  override def startDocumentSetIndexingSession(documentSetId: Long): DocumentSetIndexingSession = {
    await(indexClient.addDocumentSet(documentSetId))

    val client: Client = await(indexClient.internalClientFuture)

    new DocumentSetIndexingSessionImpl(client, documentSetId)
  }

  override def deleteDocumentSetAliasAndDocuments(documentSetId: Long): Unit = {
    await(indexClient.removeDocumentSet(documentSetId))
  }

  private class DocumentSetIndexingSessionImpl(client: Client, documentSetId: Long) extends DocumentSetIndexingSession with BulkProcessor.Listener {
    private val logger = Logger.forClass(getClass)
    private val bulkProcessor = new BulkProcessor.Builder(client, this).build
    private val allRequestsComplete = Promise[Unit]

    // Use mutable variables and locking to determine when all requests have been handled.
    private var requestInProgress: Boolean = false
    private var sessionComplete: Boolean = false

    private val DocumentSetIdField = "document_set_id"
    private val IdField = "id"
    private val TextField = "text"
    private val SuppliedIdField = "supplied_id"
    private val TitleField = "title"

    private def addFields(builder: XContentBuilder, fields: Seq[(String, Any)]): XContentBuilder = {
      fields match {
        case Nil => builder
        case (f, Some(v)) :: tail => addFields(builder.field(f, v), tail)
        case (f, None) :: tail => addFields(builder, tail)
        case (f, v) :: tail => addFields(builder.field(f, v), tail)
      }
    }

    override def indexDocument(documentSetId: Long, id: Long, text: String, title: Option[String], suppliedId: Option[String]): Unit = {
      val indexRequest = client.prepareIndex(s"documents_$documentSetId", "document")

      val source = addFields(jsonBuilder.startObject, Seq(
        (DocumentSetIdField, documentSetId),
        (IdField, id),
        (TextField, text),
        (TitleField, title),
        (SuppliedIdField, suppliedId))).endObject

      val request: IndexRequest = indexRequest.setSource(source).request

      bulkProcessor.add(request)
    }

    override def complete: Unit = {
      bulkProcessor.close
      synchronized {
        sessionComplete = true
        if (!requestInProgress) allRequestsComplete.success()
      }
    }

    override def requestsComplete: Future[Unit] = allRequestsComplete.future

    // Callbacks for a BulkProcessor.Listener, called before and after each bulk request
    // If `complete` has been called, we assume that no more requests will be made
    // so `allRequestsComplete` future is completed after  the bulk request is complete.
    override def beforeBulk(executionId: Long, request: BulkRequest): Unit = {
      logger.debug("Starting bulk indexing request")
      synchronized { requestInProgress = true }
    }

    override def afterBulk(executionId: Long, request: BulkRequest, response: BulkResponse) = {
      synchronized {
        requestInProgress = false
        if (sessionComplete) allRequestsComplete.success()
      }
      logger.debug("Bulk indexing request complete")
    }

    override def afterBulk(executionId: Long, request: BulkRequest, failure: Throwable) = {
      allRequestsComplete.failure(failure)
    }

  }
}
