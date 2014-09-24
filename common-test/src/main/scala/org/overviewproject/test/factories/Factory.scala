package org.overviewproject.test.factories

import java.sql.Timestamp
import play.api.libs.json.JsObject

import org.overviewproject.models.{ApiToken,Document,DocumentInfo,DocumentVizObject,Viz,VizObject}
import org.overviewproject.tree.orm.{Document => DeprecatedDocument,DocumentSearchResult,DocumentSet,DocumentTag,SearchResult,SearchResultState,Tag}
import org.overviewproject.util.DocumentSetVersion

/** Creates models simply.
  *
  * Usage:
  *
  *   val factory = new org.overviewproject.test.factories.Factory
  *   val documentSet = factory.documentSet()
  *   val document = factory.document(documentSetId=documentSet.id)
  *   val tag = factory.tag(documentSetId=documentSet.id)
  *   val documentTag = factory.documentTag(documentId=document.id, tagId=tag.id)
  *   ...
  *
  * Use PodoFactory for simple tests. Use DbFactory to insert rows in the
  * database while building objects, for more thorough (and slower) tests.
  */
trait Factory {
  /** Creates an ApiToken with the given parameters. */
  def apiToken(
    token: String = "token",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime),
    createdBy: String = "user@example.org",
    description: String = "description",
    documentSetId: Long = 0L
  ): ApiToken

  /** Creates a new Document with the given parameters. */
  def document(
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
  ): Document

  /** Creates a new DeprecatedDocument with the given parameters. */
  def deprecatedDocument(
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
  ): DeprecatedDocument

  /** Creates a new DocumentSet with the given parameters. */
  def documentSet(
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
  ): DocumentSet

  def documentVizObject(
    documentId: Long = 0L,
    vizObjectId: Long = 0L,
    json: Option[JsObject] = None
  ): DocumentVizObject

  def searchResult(
    id: Long = 0L,
    state: SearchResultState.Value = SearchResultState.Complete,
    documentSetId: Long = 0L,
    query: String = "query",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)
  ): SearchResult

  def documentSearchResult(
    documentId: Long,
    searchResultId: Long
  ): DocumentSearchResult

  def tag(
    id: Long = 0L,
    documentSetId: Long = 0L,
    name: String = "a tag",
    color: String = "abcdef"
  ): Tag

  def documentTag(
    documentId: Long,
    tagId: Long
  ): DocumentTag

  def viz(
    id: Long = 0L,
    documentSetId: Long = 0L,
    url: String = "http://example.org",
    apiToken: String = "api-token",
    title: String = "title",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime),
    json: JsObject = JsObject(Seq())
  ): Viz

  def vizObject(
    id: Long = 0L,
    vizId: Long = 0L,
    indexedLong: Option[Long] = None,
    indexedString: Option[String] = None,
    json: JsObject = JsObject(Seq())
  ): VizObject
}
