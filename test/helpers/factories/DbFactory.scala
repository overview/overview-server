package test.helpers.factories

import java.sql.{Connection,Timestamp}
import play.api.libs.json.JsObject
import scala.slick.jdbc.UnmanagedSession

import org.overviewproject.models._
import org.overviewproject.models.tables._
import org.overviewproject.tree.orm._
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

  override def document(
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
  ) = {
    val document = podoFactory.document(
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
    )
    DbFactory.queries.insertDocument += document
    document
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
  ) = {
    val documentSet = podoFactory.documentSet(
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
    DbFactory.queries.insertDocumentSet += documentSet
    documentSet
  }

  override def documentVizObject(
    documentId: Long = 0L,
    vizObjectId: Long = 0L,
    json: Option[JsObject] = None
  ) = {
    val dvo = podoFactory.documentVizObject(
      documentId,
      vizObjectId,
      json
    )
    DbFactory.queries.insertDocumentVizObject += dvo
    dvo
  }

  override def searchResult(
    id: Long = 0L,
    state: SearchResultState.Value = SearchResultState.Complete,
    documentSetId: Long = 0L,
    query: String = "query",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)
  ) = {
    val searchResult = podoFactory.searchResult(
      id,
      state,
      documentSetId,
      query,
      createdAt
    )
    DbFactory.queries.insertSearchResult += searchResult
    searchResult
  }

  override def documentSearchResult(
    documentId: Long,
    searchResultId: Long
  ) = {
    val documentSearchResult = podoFactory.documentSearchResult(
      documentId = documentId,
      searchResultId = searchResultId
    )
    DbFactory.queries.insertDocumentSearchResult += documentSearchResult
    documentSearchResult
  }

  override def viz(
    id: Long = 0L,
    documentSetId: Long = 0L,
    url: String = "http://example.org",
    apiToken: String = "api-token",
    title: String = "title",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime),
    json: JsObject = JsObject(Seq())
  ) = {
    val viz = podoFactory.viz(
      id,
      documentSetId,
      url,
      apiToken,
      title,
      createdAt,
      json
    )
    DbFactory.queries.insertViz += viz
    viz
  }

  override def vizObject(
    id: Long = 0L,
    vizId: Long = 0L,
    indexedLong: Option[Long] = None,
    indexedString: Option[String] = None,
    json: JsObject = JsObject(Seq())
  ) = {
    val vizObject = podoFactory.vizObject(
      id,
      vizId,
      indexedLong,
      indexedString,
      json
    )
    DbFactory.queries.insertVizObject += vizObject
    vizObject
  }
}

object DbFactory {
  object queries {
    import org.overviewproject.database.Slick.simple._

    // Compile queries once, instead of once per test
    val insertDocument = (Documents returning Documents).insertInvoker
    val insertDocumentSearchResult = (DocumentSearchResults returning DocumentSearchResults).insertInvoker
    val insertDocumentSet = (DocumentSets returning DocumentSets).insertInvoker
    val insertDocumentVizObject = (DocumentVizObjects returning DocumentVizObjects).insertInvoker
    val insertSearchResult = (SearchResults returning SearchResults).insertInvoker
    val insertViz = (Vizs returning Vizs).insertInvoker
    val insertVizObject = (VizObjects returning VizObjects).insertInvoker
  }
}
