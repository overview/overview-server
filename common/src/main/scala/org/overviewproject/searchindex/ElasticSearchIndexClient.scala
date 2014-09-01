package org.overviewproject.searchindex

import org.elasticsearch.action.{ActionListener,ActionRequest,ActionRequestBuilder,ActionResponse}
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.index.query.{FilterBuilders,QueryBuilders}
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future,Promise}

import org.overviewproject.tree.orm.Document // FIXME should be model

trait ElasticSearchIndexClient extends IndexClient {
  @volatile private var connected = false

  /** Calls connect(), then adds necessary data structures to ElasticSearch
    * if they aren't already there.
    *
    * After accessing this variable, you can assume:
    *
    * * There is a "documents_v1" index, with a "document" mapping.
    * * There is a "documents" alias to the "documents_v1" index.
    */
  protected lazy val clientFuture: Future[Client] = {
    connected = true
    connect.flatMap { client =>
      ensureInitializedImpl(client)
        .map(_ => client)
    }
  }

  /** Returns an ElasticSearch client handle.
    *
    * This should call ensureInitialized() before resolving.
    */
  protected def connect: Future[Client]

  /** Releases all resources created during connect. */
  protected def disconnect: Future[Unit]

  private val DocumentSetIdField = "document_set_id"
  protected val DocumentTypeName = "document"
  protected val IndexName = "documents"
  protected val RealIndexName = "documents_v1"
  protected def aliasName(documentSetId: Long) = s"documents_$documentSetId"
  protected val MaxNResults = 10000000 // causes OOM if too high
  protected val Mapping = s"""{
    "document": {
      "properties": {
        "$DocumentSetIdField": { "type": "long" },
        "id":                  { "type": "long" },
        "text":                { "type": "string" },
        "supplied_id":         { "type": "string" },
        "title":               { "type": "string" }
      }
    }
  }"""

  def close: Future[Unit] = {
    if (connected) {
      disconnect
    } else {
      Future.successful(Unit)
    }
  }

  /** Executes an ElasticSearch request asynchronously.
    *
    * Returns a Future. If the request succeeds, calls onSuccess(response) and
    * the future resolves to the return value. If onSuccess() throws an
    * exception, the Future fails. If the ElasticSearch execution fails, the
    * Future fails.
    */
  private def execute[Request <: ActionRequest[Request],Response <: ActionResponse,RequestBuilder <: ActionRequestBuilder[Request,Response,RequestBuilder]](req: ActionRequestBuilder[Request,Response,RequestBuilder]): Future[Response] = {
    val promise = Promise[Response]()

    req.execute(new ActionListener[Response] {
      override def onResponse(result: Response) = promise.success(result)
      override def onFailure(t: Throwable) = promise.failure(t)
    })

    promise.future
  }

  private def indexExists(client: Client): Future[Boolean] = {
    execute(client.admin.indices.prepareExists(RealIndexName))
      .map(_.isExists)
  }

  private def createIndex(client: Client): Future[Unit] = {
    val settings = ImmutableSettings.settingsBuilder
      .put("index.store.type", "memory")
      .put("index.number_of_shards", 1)
      .put("index.number_of_replicas", 0)

    val req = client.admin.indices.prepareCreate(RealIndexName)
      .setSettings(settings)
      .addMapping(DocumentTypeName, Mapping)

    execute(req)
      .map(_ => Unit)
  }

  private def aliasExists(client: Client): Future[Boolean] = {
    execute(client.admin.indices.prepareExistsAliases(IndexName))
      .map(_.isExists)
  }

  private def createAlias(client: Client): Future[Unit] = {
    execute(client.admin.indices.prepareAliases.addAlias(RealIndexName, IndexName))
      .map(_ => Unit)
  }

  private def ensureIndexExists(client: Client): Future[Unit] = {
    indexExists(client).flatMap((exists) =>
      if (exists) {
        Future.successful(Unit)
      } else {
        createIndex(client)
      }
    )
  }

  private def ensureAliasExists(client: Client): Future[Unit] = {
    aliasExists(client).flatMap((exists) =>
      if (exists) {
        Future.successful(Unit)
      } else {
        createAlias(client)
      }
    )
  }

  private def ensureInitializedImpl(client: Client): Future[Unit] = {
    ensureIndexExists(client)
      .flatMap { _ => ensureAliasExists(client) }
  }

  private def addDocumentSetImpl(client: Client, id: Long): Future[Unit] = {
    val filter = FilterBuilders.termFilter(DocumentSetIdField, id)
    val alias = client.admin.indices.prepareAliases()
      .addAlias(RealIndexName, aliasName(id), filter)

    execute(alias)
      .map(_ => Unit)
  }

  private def removeDocumentSetImpl(client: Client, id: Long): Future[Unit] = {
    val unalias = client.admin.indices.prepareAliases()
      .removeAlias(RealIndexName, aliasName(id))

    val delete = client.prepareDeleteByQuery(IndexName)
      .setTypes(DocumentTypeName)
      .setQuery(QueryBuilders.termQuery(DocumentSetIdField, id))

    Future.sequence(Seq(execute(unalias), execute(delete)))
      .map(_ => Unit)
  }

  private def addDocumentsImpl(client: Client, documents: Iterable[Document]): Future[Unit] = {
    val bulkBuilder = client.prepareBulk()
    val baseReq = client.prepareIndex(IndexName, DocumentTypeName)

    documents.foreach { document =>
      bulkBuilder.add(
        client.prepareIndex(IndexName, DocumentTypeName)
          .setSource(Json.obj(
            "document_set_id" -> document.documentSetId,
            "id" -> document.id,
            "text" -> document.text,
            "title" -> document.title,
            "supplied_id" -> document.suppliedId
          ).toString)
          .request
      )
    }

    execute(bulkBuilder).map { response =>
      if (response.hasFailures) {
        throw new Exception(response.buildFailureMessage)
      } else {
        Unit
      }
    }
  }

  private def searchForIdsImpl(client: Client, documentSetId: Long, q: String) = {
    val query = QueryBuilders.boolQuery
      .must(QueryBuilders.termQuery("document_set_id", documentSetId))
      .must(QueryBuilders.queryString(q))

    val req = client.prepareSearch(IndexName)
      .setTypes(DocumentTypeName)
      .setQuery(query)
      .setSize(MaxNResults)
      .addField("id")

    execute(req).map { response =>
      response.getShardFailures.headOption match {
        // Casting to String then Long because ElasticSearch sends JSON and
        // forgets the type. It's sometimes Integer, sometimes Long.
        // https://groups.google.com/forum/#!searchin/elasticsearch/getsource$20integer$20long/elasticsearch/jxIY22TmA8U/PyqZPPyYQ0gJ
        case None => {
          response.getHits
            .getHits
            .map(_.field("id").value[Object].toString.toLong)
            .toSeq
        }
        case Some(failure) => throw new Exception(failure.reason)
      }
    }
  }

  private def refreshImpl(client: Client): Future[Unit] = {
    val req = client.admin.indices.prepareRefresh(IndexName)

    execute(req).map { response =>
      response.getShardFailures.headOption match {
        case None => Unit
        case Some(failure) => throw new Exception(failure.reason)
      }
    }
  }

  override def addDocumentSet(id: Long) = clientFuture.flatMap(addDocumentSetImpl(_, id))
  override def removeDocumentSet(id: Long) = clientFuture.flatMap(removeDocumentSetImpl(_, id))
  override def addDocuments(documents: Iterable[Document]) = clientFuture.flatMap(addDocumentsImpl(_, documents))
  override def searchForIds(documentSetId: Long, q: String) = clientFuture.flatMap(searchForIdsImpl(_, documentSetId, q))
  override def refresh = clientFuture.flatMap(refreshImpl(_))
}
