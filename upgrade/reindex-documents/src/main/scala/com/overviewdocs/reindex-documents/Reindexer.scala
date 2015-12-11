package com.overviewdocs.upgrade.reindex_documents

import com.ning.http.client.AsyncHttpClient
import com.fasterxml.jackson.core.{JsonFactory,JsonGenerator}
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import scala.collection.mutable

class Reindexer(url: ElasticSearchUrl, clusterName: String, indexName: String) {
  private val DocumentsAlias = "documents"
  private val DocumentTypeName = "document"
  private val DocumentSetIdField = "document_set_id"
  private val BatchSize = 100
  private val IndexRegex = """^(documents_v\d+)$""".r
  private val AliasRegex = """^documents_(\d+)$""".r

  private val jsonFactory = (new JsonFactory).configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)

  if (IndexRegex.findFirstIn(indexName).isEmpty) {
    throw new Exception(s"Invalid index name `$indexName`. It must look like `documents_v2`: that is, 'documents_v' plus digits.")
  }

  private val httpClient = new AsyncHttpClient()

  def close: Unit = httpClient.close

  private def POST(path: String): Unit = {
    val response = httpClient.preparePost(url + path).execute.get
    if (response.getStatusCode != 200) {
      throw new Exception("Error from server: " + response.getResponseBody)
    }
  }

  private def POST(path: String, body: Array[Byte]): Unit = {
    val response = httpClient.preparePost(url + path).setBody(body).execute.get
    if (response.getStatusCode != 200) {
      throw new Exception("Error from server: " + response.getResponseBody)
    }
  }

  private def POST(path: String, body: String): Unit = {
    val response = httpClient.preparePost(url + path).setBody(body).execute.get
    if (response.getStatusCode != 200) {
      throw new Exception("Error from server: " + response.getResponseBody)
    }
  }

  /** Point "documents_N" to the new index, leaving existing aliases intact.
    *
    * We need to do this before we put any documents in the new index, so
    * searches will include them. For existing document sets, that will mean
    * two indices per alias.
    */
  def addDocumentSetAliases(database: Database): Unit = {
    val jsons: Seq[String] = database.getDocumentSetIds.map { case (id, _) =>
      s"""{"add":{"index":"$indexName","alias":"documents_$id","filter":{"term":{"document_set_id":$id}}}}"""
    }

    val request: String = s"""{"actions":[${jsons.mkString(",")}]}"""
    POST("/_aliases", request)
  }

  /** Sets the Documents alias to point to indexName.
    *
    * This alias can only point to one index at a time: otherwise, all writes
    * to the alias will fail.
    */
  def updateDocumentsAlias: Unit = {
    POST("/_aliases", s"""{"actions":[
      { "remove": { "index": "_all", "alias": "documents" } },
      { "add": { "index": "$indexName", "alias": "documents" } }
    ]}""")
  }

  def reindexAllDocumentSets(database: Database): Unit = {
    System.err.println("Loading document set IDs...")
    val documentSetIds = database.getDocumentSetIds
    System.err.println(s"Starting reindex of ${documentSetIds.length} document sets...")
    documentSetIds.foreach { case (id, nDocuments) =>
      reindexDocumentSet(id, nDocuments, database)
    }
    System.err.println("Reindexed all document sets")
  }

  def reindexDocumentSet(documentSetId: Long, nDocuments: Int, database: Database): Unit = {
    System.err.print(s"Reindexing ${nDocuments} documents in document set ${documentSetId}")

    database.forEachBatchOfDocumentsInSet(documentSetId, BatchSize) { documents: Seq[Document] =>
      System.err.print(".")
      System.err.flush
      indexDocuments(documents)
    }

    System.err.println

    System.err.println("Removing old aliases...")
    removeOldDocumentSetAliases(documentSetId)

    System.err.println(s"Done reindexing document set ${documentSetId}")
  }

  private def removeOldDocumentSetAliases(id: Long): Unit = {
    POST("/_aliases", s"""{"actions":[
      { "remove": { "index": "_all", "alias": "documents_$id" } },
      { "add": {"index":"$indexName","alias":"documents_$id","filter":{"term":{"document_set_id":$id}}}}
    ]}""")
  }

  private def indexDocuments(documents: Seq[Document]): Unit = {
    val baos = new ByteArrayOutputStream

    documents.foreach { document =>
      baos.write(s"""{"index":{"_index":"$indexName","_type":"document","_id":"${document.id}"}}\n""".getBytes(StandardCharsets.UTF_8))
      val j = jsonFactory.createGenerator(baos)
      j.writeStartObject
      j.writeNumberField("document_set_id", document.documentSetId)
      j.writeStringField("supplied_id", document.suppliedId)
      j.writeStringField("title", document.title)
      j.writeStringField("text", document.text)
      j.writeEndObject
      j.close
      baos.write('\n'.toByte)
    }

    POST("/_bulk", baos.toByteArray)
  }
}
