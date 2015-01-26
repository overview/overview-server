package org.overviewproject.test.factories

import java.sql.Timestamp
import java.util.{Date,UUID}
import play.api.libs.json.JsObject
import org.overviewproject.models._
import org.overviewproject.tree.orm.{ Document => DeprecatedDocument }
import org.overviewproject.util.DocumentSetVersion

/**
 * Creates models simply.
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
    createdAt: Timestamp = now,
    createdBy: String = "user@example.org",
    description: String = "description",
    documentSetId: Long = 0L): ApiToken

  /** Creates a new Document with the given parameters. */
  def document(
    id: Long = 0L,
    documentSetId: Long = 0L,
    url: Option[String] = None,
    suppliedId: String = "",
    title: String = "",
    keywords: Seq[String] = Seq(),
    createdAt: Date = new Date(1234L),
    pageNumber: Option[Int] = None,
    fileId: Option[Long] = None,
    pageId: Option[Long] = None,
    text: String = ""): Document

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
    createdAt: Timestamp = new Timestamp(1234L),
    fileId: Option[Long] = None,
    pageId: Option[Long] = None,
    pageNumber: Option[Int] = None): DeprecatedDocument

  /** Creates a new DocumentSet with the given parameters. */
  def documentSet(
    id: Long = 0L,
    title: String = "",
    query: Option[String] = None,
    isPublic: Boolean = false,
    createdAt: Timestamp = now,
    documentCount: Int = 4,
    documentProcessingErrorCount: Int = 3,
    importOverflowCount: Int = 2,
    uploadedFileId: Option[Long] = None,
    version: Int = DocumentSetVersion.current,
    deleted: Boolean = false): DocumentSet

  def documentSetUser(
    documentSetId: Long = 0L,
    userEmail: String = "user@example.com",
    role: DocumentSetUser.Role = DocumentSetUser.Role(1)  
  ): DocumentSetUser

  def documentTag(documentId: Long, tagId: Long): DocumentTag

  def documentStoreObject(
    documentId: Long = 0L,
    storeObjectId: Long = 0L,
    json: Option[JsObject] = None
  ): DocumentStoreObject

  def fileGroup(
    id: Long = 0L,
    userEmail: String = "user@example.org",
    apiToken: Option[String] = None,
    completed: Boolean = false,
    deleted: Boolean = false
  ): FileGroup

  def groupedFileUpload(
    id: Long = 0L,
    fileGroupId: Long = 0L,
    guid: UUID = new UUID(0L, 0L),
    contentType: String = "application/octet-stream",
    name: String = "filename.abc",
    size: Long = 1024L,
    uploadedSize: Long = 1024L,
    contentsOid: Long = 0L
  ): GroupedFileUpload

  def node(
    id: Long = 0L,
    rootId: Long = 0L,
    parentId: Option[Long] = None,
    description: String = "",
    cachedSize: Int = 0,
    isLeaf: Boolean = true): Node

  def nodeDocument(nodeId: Long, documentId: Long): NodeDocument

  def plugin(
    id: UUID = new UUID(0L, 0L),
    name: String = "name",
    description: String = "description",
    url: String = "http://example.org"): Plugin

  def tag(
    id: Long = 0L,
    documentSetId: Long = 0L,
    name: String = "a tag",
    color: String = "abcdef"): Tag

  def tree(
    id: Long = 0L,
    documentSetId: Long = 0L,
    rootNodeId: Long = 0L,
    jobId: Long = 0L,
    title: String = "title",
    documentCount: Int = 10,
    lang: String = "en",
    description: String = "description",
    suppliedStopWords: String = "supplied stop words",
    importantWords: String = "important words",
    createdAt: Timestamp = now
  ): Tree

  def view(
    id: Long = 0L,
    documentSetId: Long = 0L,
    url: String = "http://example.org",
    apiToken: String = "api-token",
    title: String = "title",
    createdAt: Timestamp = now
  ): View

  def store(
    id: Long = 0L,
    apiToken: String = "token",
    json: JsObject = JsObject(Seq())): Store

  def storeObject(
    id: Long = 0L,
    storeId: Long = 0L,
    indexedLong: Option[Long] = None,
    indexedString: Option[String] = None,
    json: JsObject = JsObject(Seq())): StoreObject

  def page(
    id: Long = 0L,
    fileId: Long = 0L,
    pageNumber: Int = 1,
    dataLocation: String = "pagebytea:123",
    dataSize: Long = 9L,
    data: Option[Array[Byte]] = Some("page text".getBytes("utf-8")),
    text: Option[String] = Some("page text"),
    dataErrorMessage: Option[String] = None,
    textErrorMessage: Option[String] = None): Page

  def file(
    id: Long = 0L,
    referenceCount: Int = 1,
    name: String = "filename",
    contentsLocation: String = "contents:location",
    contentsSize: Long = 2L,
    viewLocation: String = "view:location",
    viewSize: Long = 3L
  ): File

  def uploadedFile(
    id: Long = 0L,
    contentDisposition: String = "attachment; filename=file.csv",
    contentType: String = "text/csv",
    size: Long = 0L,
    uploadedAt: Timestamp = now
  ): UploadedFile 
  
  def documentProcessingError(
    id: Long = 0L,
    documentSetId: Long = 0L,
    textUrl: String = "http://example.com",
    message: String = "error message",
    statusCode: Option[Int] = None,
    headers: Option[String] = None
  ): DocumentProcessingError
  
  def documentSetCreationJob(
    id: Long = 0L,
    documentSetId: Long = 0L,
    jobType: DocumentSetCreationJobType.Value = DocumentSetCreationJobType.Recluster,
    retryAttempts: Int = 0,
    lang: String = "en",
    suppliedStopWords: String = "",
    importantWords: String = "",
    splitDocuments: Boolean = false,
    documentcloudUsername: Option[String] = None,
    documentcloudPassword: Option[String] = None,
    contentsOid: Option[Long] = None,
    fileGroupId: Option[Long] = None,
    sourceDocumentSetId: Option[Long] = None,
    treeTitle: Option[String] = None,
    treeDescription: Option[String] = None,
    tagId: Option[Long] = None,
    state: DocumentSetCreationJobState.Value = DocumentSetCreationJobState.NotStarted,
    fractionComplete: Double = 0.0,
    statusDescription: String = ""
  ): DocumentSetCreationJob
  

  def documentSetCreationJobNode(
    documentSetCreationJobId: Long = 0L,
    nodeId: Long = 0L
  ): DocumentSetCreationJobNode

  def tempDocumentSetFile(documentSetId: Long, fileId: Long): TempDocumentSetFile
  
  private def now: Timestamp = new Timestamp(scala.compat.Platform.currentTime)
}
