package org.overviewproject.test.factories

import java.sql.{Connection,Timestamp}
import play.api.libs.json.JsObject
import scala.slick.jdbc.UnmanagedSession

import org.overviewproject.models.tables._
import org.overviewproject.models.{ApiToken,Document,DocumentInfo,DocumentVizObject,Viz,VizObject}
import org.overviewproject.tree.orm.{Document => DeprecatedDocument,DocumentSearchResult,DocumentSet,DocumentTag,Node,NodeDocument,SearchResult,SearchResultState,Tag}
import org.overviewproject.util.DocumentSetVersion

/** Creates objects in the database while returning them.
  *
  * Pass `0L` into any primary key column to have DbFactory select an ID
  * automatically.
  *
  * @see Factory
  */
class DbFactory(connection: Connection) extends Factory {
  private implicit val session = new UnmanagedSession(connection)
  private val podoFactory = PodoFactory
  val q = DbFactory.queries

  override def apiToken(
    token: String = "token",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime),
    createdBy: String = "user@example.org",
    description: String = "description",
    documentSetId: Long = 0L
  ) = q.insertApiToken += podoFactory.apiToken(
    token,
    createdAt,
    createdBy,
    description,
    documentSetId
  )

  override def document(
    id: Long = 0L,
    documentSetId: Long = 0L,
    url: Option[String] = None,
    suppliedId: String = "",
    title: String = "",
    keywords: Seq[String] = Seq(),
    pageNumber: Option[Int] = None,
    fileId: Option[Long] = None,
    pageId: Option[Long] = None,
    text: String = ""
  ) = q.insertDocument += podoFactory.document(
    id,
    documentSetId,
    url,
    suppliedId,
    title,
    keywords,
    pageNumber,
    fileId,
    pageId,
    text
  )

  override def deprecatedDocument(
    id: Long = 0L,
    documentSetId: Long = 0L,
    description: String = "",
    title: Option[String] = None,
    suppliedId: Option[String] = None,
    text: Option[String] = None,
    url: Option[String] = None,
    documentcloudId: Option[String] = None,
    fileId: Option[Long] = None,
    pageId: Option[Long] = None,
    pageNumber: Option[Int] = None
  ) = (q.insertDocument += podoFactory.deprecatedDocument(
    id,
    documentSetId,
    description,
    title,
    suppliedId,
    text,
    url,
    documentcloudId,
    fileId,
    pageId,
    pageNumber
  ).toDocument).toDeprecatedDocument

  override def documentSearchResult(documentId: Long, searchResultId: Long) = {
    q.insertDocumentSearchResult += podoFactory.documentSearchResult(
      documentId = documentId,
      searchResultId = searchResultId
    )
  }

  override def documentSet(
    id: Long = 0L,
    title: String = "",
    query: Option[String] = None,
    isPublic: Boolean = false,
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime),
    documentCount: Int = 4,
    documentProcessingErrorCount: Int = 3,
    importOverflowCount: Int = 2,
    uploadedFileId: Option[Long] = None,
    version: Int = DocumentSetVersion.current,
    deleted: Boolean = false
  ) = q.insertDocumentSet += podoFactory.documentSet(
    id,
    title,
    query,
    isPublic,
    createdAt,
    documentCount,
    documentProcessingErrorCount,
    importOverflowCount,
    uploadedFileId,
    version,
    deleted
  )

  override def documentTag(documentId: Long, tagId: Long) = {
    q.insertDocumentTag += podoFactory.documentTag(documentId, tagId)
  }

  override def documentVizObject(
    documentId: Long = 0L,
    vizObjectId: Long = 0L,
    json: Option[JsObject] = None
  ) = q.insertDocumentVizObject += podoFactory.documentVizObject(
    documentId,
    vizObjectId,
    json
  )

  override def node(
    id: Long = 0L,
    rootId: Long = 0L,
    parentId: Option[Long] = None,
    description: String = "",
    cachedSize: Int = 0,
    isLeaf: Boolean = true
  ) = q.insertNode += podoFactory.node(
    id,
    rootId,
    parentId,
    description,
    cachedSize,
    isLeaf
  )

  override def nodeDocument(nodeId: Long, documentId: Long) = {
    q.insertNodeDocument += podoFactory.nodeDocument(nodeId, documentId)
  }

  override def searchResult(
    id: Long = 0L,
    state: SearchResultState.Value = SearchResultState.Complete,
    documentSetId: Long = 0L,
    query: String = "query",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)
  ) = q.insertSearchResult += podoFactory.searchResult(
    id,
    state,
    documentSetId,
    query,
    createdAt
  )

  override def tag(
    id: Long = 0L,
    documentSetId: Long = 0L,
    name: String = "a tag",
    color: String = "abcdef"
  ) = q.insertTag += podoFactory.tag(id, documentSetId, name, color)

  override def viz(
    id: Long = 0L,
    documentSetId: Long = 0L,
    url: String = "http://example.org",
    apiToken: String = "api-token",
    title: String = "title",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime),
    json: JsObject = JsObject(Seq())
  ) = q.insertViz += podoFactory.viz(
    id,
    documentSetId,
    url,
    apiToken,
    title,
    createdAt,
    json
  )

  override def vizObject(
    id: Long = 0L,
    vizId: Long = 0L,
    indexedLong: Option[Long] = None,
    indexedString: Option[String] = None,
    json: JsObject = JsObject(Seq())
  ) = q.insertVizObject += podoFactory.vizObject(
    id,
    vizId,
    indexedLong,
    indexedString,
    json
  )
}

object DbFactory {
  object queries {
    import org.overviewproject.database.Slick.simple._

    // Compile queries once, instead of once per test
    val insertApiToken = (ApiTokens returning ApiTokens).insertInvoker
    val insertDocument = (Documents returning Documents).insertInvoker
    val insertDocumentSearchResult = (DocumentSearchResults returning DocumentSearchResults).insertInvoker
    val insertDocumentSet = (DocumentSets returning DocumentSets).insertInvoker
    val insertDocumentTag = (DocumentTags returning DocumentTags).insertInvoker
    val insertDocumentVizObject = (DocumentVizObjects returning DocumentVizObjects).insertInvoker
    val insertNode = (Nodes returning Nodes).insertInvoker
    val insertNodeDocument = (NodeDocuments returning NodeDocuments).insertInvoker
    val insertSearchResult = (SearchResults returning SearchResults).insertInvoker
    val insertTag = (Tags returning Tags).insertInvoker
    val insertViz = (Vizs returning Vizs).insertInvoker
    val insertVizObject = (VizObjects returning VizObjects).insertInvoker
  }
}
