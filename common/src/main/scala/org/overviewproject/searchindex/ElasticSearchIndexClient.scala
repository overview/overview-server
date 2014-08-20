package org.overviewproject.searchindex

import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.QueryBuilders
import play.api.libs.json.Json
import scala.concurrent.{Future,Promise}

import org.overviewproject.tree.orm.Document // FIXME should be model

class ElasticSearchIndexClient(protected val client: Client, protected val indexName: String) extends IndexClient {
  protected val DocumentTypeName = "document"
  protected val MaxNResults = 10000000

  protected val Mapping = """{
    "document": {
      "properties": {
        "document_set_id": { "type": "long" },
        "id":              { "type": "long" },
        "text":            { "type": "string" },
        "supplied_id":     { "type": "string" },
        "title":           { "type": "string" }
      }
    }
  }"""

  // TODO
  override def addDocumentSet(id: Long) = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Unit)
  }

  // TODO
  //
  // We'll die before removing anything!
  override def removeDocumentSet(id: Long) = ???

  override def addDocuments(documents: Iterable[Document]) = {
    val bulkBuilder = client.prepareBulk()
    val baseReq = client.prepareIndex(indexName, DocumentTypeName)

    documents.foreach { document =>
      bulkBuilder.add(
        client.prepareIndex(indexName, DocumentTypeName)
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

    val promise = Promise[Unit]()

    bulkBuilder.execute(new ActionListener[BulkResponse] {
      override def onResponse(response: BulkResponse) = {
        if (response.hasFailures) {
          promise.failure(new Exception(response.buildFailureMessage))
        } else {
          promise.success(Unit)
        }
      }

      override def onFailure(t: Throwable) = promise.failure(t)
    })

    promise.future
  }

  override def searchForIds(documentSetId: Long, q: String) = {
    val promise = Promise[Seq[Long]]()

    val query = QueryBuilders.boolQuery
      .must(QueryBuilders.termQuery("document_set_id", documentSetId))
      .must(QueryBuilders.queryString(q))

    client.prepareSearch(indexName)
      .setTypes(DocumentTypeName)
      .setQuery(query)
      .setSize(MaxNResults)
      .addField("id")
      .execute(new ActionListener[SearchResponse] {
        override def onResponse(response: SearchResponse) = {
          response.getShardFailures.headOption match {
            // Casting to String then Long because ElasticSearch sends JSON and
            // forgets the type. It's sometimes Integer, sometimes Long.
            // https://groups.google.com/forum/#!searchin/elasticsearch/getsource$20integer$20long/elasticsearch/jxIY22TmA8U/PyqZPPyYQ0gJ
            case None => {
              val ids = response.getHits
                .getHits
                .map(_.field("id").value[Object].toString.toLong)
              promise.success(ids)
            }
            case Some(failure) => promise.failure(new Exception(failure.reason))
          }
        }

        override def onFailure(t: Throwable) = promise.failure(t)
      })

    promise.future
  }

  override def refresh() = {
    val promise = Promise[Unit]()

    client.admin.indices.prepareRefresh(indexName)
      .execute(new ActionListener[RefreshResponse] {
        override def onResponse(response: RefreshResponse) = {
          response.getShardFailures.headOption match {
            case None => promise.success(Unit)
            case Some(failure) => promise.failure(new Exception(failure.reason))
          }
        }

        override def onFailure(t: Throwable) = promise.failure(t)
      })

    promise.future
  }
}
