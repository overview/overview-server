package test.helpers.factories

import java.sql.Timestamp
import play.api.libs.json.JsObject
import scala.util.Random

import org.overviewproject.models._
import org.overviewproject.tree.orm._
import org.overviewproject.util.DocumentSetVersion

/** Plain Old Data Object factory.
  *
  * Pass `0L` into any primary key column to generate a unique ID
  * automatically.
  *
  * @see Factory
  */
object PodoFactory extends Factory {
  private val random = new Random()

  /** Generate a non-conflicting ID, if the passed parameter is 0L. */
  private def getId(idOr0: Long): Long = {
    if (idOr0 == 0L) {
      random.nextLong
    } else {
      idOr0
    }
  }

  /** Generate a non-conflicting ID, if the passed parameter is 0L.
    *
    * The ID will be a positive Int cast to a Long.
    */
  private def get32BitId(idOr0: Long): Long = {
    if (idOr0 == 0L) {
      math.abs(random.nextInt)
    } else {
      idOr0
    }
  }

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
  ) = Document(
    getId(documentSetId),
    description,
    title,
    suppliedId,
    text,
    url,
    documentcloudId,
    fileId,
    pageId,
    pageNumber,
    getId(id)
  )

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
  ) = DocumentSet(
    get32BitId(id),
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

  override def documentVizObject(
    documentId: Long = 0L,
    vizObjectId: Long = 0L,
    json: Option[JsObject] = None
  ) = DocumentVizObject(
    documentId,
    vizObjectId,
    json
  )

  override def searchResult(
    id: Long = 0L,
    state: SearchResultState.Value = SearchResultState.Complete,
    documentSetId: Long = 0L,
    query: String = "query",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)
  ) = SearchResult(
    state,
    documentSetId,
    query,
    createdAt,
    getId(id)
  )

  override def documentSearchResult(
    documentId: Long,
    searchResultId: Long
  ) = DocumentSearchResult(documentId, searchResultId)

  override def viz(
    id: Long = 0L,
    documentSetId: Long = 0L,
    url: String = "http://example.org",
    apiToken: String = "api-token",
    title: String = "title",
    createdAt: Timestamp = new Timestamp(0L),
    json: JsObject = JsObject(Seq())
  ) = Viz(
    getId(id),
    getId(documentSetId),
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
  ) = VizObject(
    getId(id),
    getId(vizId),
    indexedLong,
    indexedString,
    json
  )
}
