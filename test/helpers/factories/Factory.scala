package test.helpers.factories

import java.sql.Timestamp

import org.overviewproject.models._
import org.overviewproject.tree.orm._
import org.overviewproject.util.DocumentSetVersion

/** Creates models simply.
  *
  * Usage:
  *
  *   val factory = new test.helpers.factories.Factory
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
  /** Creates a new Document with the given parameters. */
  def document(
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
  ): Document

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
}
