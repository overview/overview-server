package org.overviewproject.upgrade.reindex_documents

import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.index.query.FilterBuilders

class Reindexer(url: ElasticSearchUrl, clusterName: String, indexName: String) {
  val DocumentsAlias = "documents"
  val DocumentTypeName = "document"
  val DocumentSetIdField = "document_set_id"
  val BatchSize = 250

  lazy val client = {
    val settings = ImmutableSettings.settingsBuilder
      .put("cluster.name", clusterName)

    val ret = new TransportClient(settings)
    ret.addTransportAddress(new InetSocketTransportAddress(url.host, url.port))
    ret
  }

  /** Point "documents_N" to the new index, leaving existing aliases intact.
    *
    * We need to do this before we put any documents in the new index, so
    * searches will include them. For existing document sets, that will mean
    * two indices per alias.
    */
  def addDocumentSetAliases(database: Database): Unit = {
    def idToFilter(id: Long) = FilterBuilders.termFilter(DocumentSetIdField, id)

    val builder = client.admin.indices.prepareAliases
    database.getDocumentSetIds.foreach { documentSetId: Long =>
      builder.addAlias(indexName, aliasName(documentSetId), idToFilter(documentSetId))
    }
    builder.execute.get
  }

  /** Sets the Documents alias to point to indexName.
    *
    * This alias can only point to one index at a time: otherwise, all writes
    * to the alias will fail.
    */
  def updateDocumentsAlias: Unit = {
    val existingIndices = indicesForAlias(DocumentsAlias)

    val indicesToRemove = existingIndices - indexName
    val indicesToAdd = Set(indexName) -- existingIndices

    if (indicesToRemove.nonEmpty || indicesToAdd.nonEmpty) {
      val builder = client.admin.indices.prepareAliases
      indicesToRemove.foreach(i => builder.removeAlias(i, DocumentsAlias))
      indicesToAdd.foreach(i => builder.addAlias(i, DocumentsAlias))
      builder.execute.get
    }
  }

  def reindexAllDocumentSets(database: Database): Unit = {
    System.err.println("Loading document set IDs...")
    val documentSetIds = database.getDocumentSetIds
    System.err.println(s"Starting/resuming reindex of ${documentSetIds.length} document sets")
    documentSetIds.foreach { id => reindexDocumentSet(id, database) }
    System.err.println("Reindexed all document sets")
  }

  def reindexDocumentSet(documentSetId: Long, database: Database): Unit = {
    System.err.println(s"Starting reindex of document set ${documentSetId}...")

    database.forEachBatchOfDocumentsInSet(documentSetId, BatchSize) { documents: Seq[Document] =>
      System.err.println(s"Indexing ${documents.length} documents...")
      indexDocuments(documents)
    }

    System.err.println("Removing old aliases...")
    removeOldDocumentSetAliases(documentSetId)

    System.err.println(s"Done reindexing document set ${documentSetId}")
  }

  private def indicesForAlias(alias: String): Set[String] = {
    client.admin.indices
      .prepareGetAliases(alias)
      .execute.get
      .getAliases.keys.toArray.map(_.toString).toSet
  }

  private def aliasName(documentSetId: Long): String = "documents_" + documentSetId

  private def removeOldDocumentSetAliases(documentSetId: Long): Unit = {
    val alias = aliasName(documentSetId)
    val otherIndices = indicesForAlias(alias) - indexName

    if (otherIndices.nonEmpty) {
      val builder = client.admin.indices.prepareAliases
      otherIndices.foreach(index => builder.removeAlias(index, alias))
      builder.execute.get
    }
  }

  private def indexDocuments(documents: Seq[Document]): Unit = {
    var builder = client.prepareBulk

    documents.foreach { document =>
      builder.add(
        client
          .prepareIndex(indexName, DocumentTypeName)
          .setSource(document.toJsonString)
          .request
      )
    }

    builder.execute.get
  }
}
