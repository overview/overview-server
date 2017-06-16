package com.overviewdocs.test.factories

import java.sql.Timestamp
import java.util.{Date,UUID}
import java.time.Instant
import play.api.libs.json.JsObject

import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models._

/**
 * Creates models simply.
 *
 * Usage:
 *
 *   val factory = new com.overviewdocs.test.factories.Factory
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
    documentSetId: Option[Long] = None
  ): ApiToken

  /** Creates a CloneJob with the given parameters. */
  def cloneJob(
    id: Int = 0,
    sourceDocumentSetId: Long = 0L,
    destinationDocumentSetId: Long = 0L,
    stepNumber: Short = 0.toShort,
    cancelled: Boolean = false,
    createdAt: Instant = Instant.now
  ): CloneJob

  /** Creates a CsvImport with the given parameters. */
  def csvImport(
    id: Long = 0L,
    documentSetId: Long = 0L,
    filename: String = "import.csv",
    charsetName: String = "utf-8",
    lang: String = "en",
    loid: Long = 0L,
    nBytes: Long = 1L,
    nBytesProcessed: Long = 0L,
    nDocuments: Int = 0,
    cancelled: Boolean = false,
    estimatedCompletionTime: Option[Instant] = None,
    createdAt: Instant = Instant.now
  ): CsvImport

  def danglingNode(rootNodeId: Long = 0L): DanglingNode

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
    displayMethod: DocumentDisplayMethod.Value = DocumentDisplayMethod.auto,
    isFromOcr: Boolean = false,
    metadataJson: JsObject = JsObject(Seq()),
    thumbnailLocation: Option[String] = None,
    pdfNotes: PdfNoteCollection = PdfNoteCollection(Array()),
    text: String = ""): Document

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
    metadataSchema: MetadataSchema = MetadataSchema.empty,
    deleted: Boolean = false
  ): DocumentSet

  /** Creates a DocumentCloudImport with the given parameters. */
  def documentCloudImport(
    id: Int = 0,
    documentSetId: Long = 0L,
    query: String = "query",
    username: String = "",
    password: String = "",
    splitPages: Boolean = false,
    lang: String = "en",
    nIdListsFetched: Int = 0,
    nIdListsTotal: Option[Int] = None,
    nFetched: Int = 0,
    nTotal: Option[Int] = None,
    cancelled: Boolean = false,
    createdAt: Instant = Instant.now
  ): DocumentCloudImport

  /** Creates a DocumentCloudImportIdList with the given parameters. */
  def documentCloudImportIdList(
    id: Int = 0,
    documentCloudImportId: Int = 0,
    pageNumber: Int = 0,
    idsString: String = "123-foo,1\n234-bar,2",
    nDocuments: Int = 0,
    nPages: Int = 0
  ): DocumentCloudImportIdList

  /** Creates a new DocumentSetReindexJob with the given parameters. */
  def documentSetReindexJob(
    id: Long = 0L,
    documentSetId: Long = 0L,
    lastRequestedAt: Instant = Instant.now,
    startedAt: Option[Instant] = None,
    progress: Double = 0.0
  ): DocumentSetReindexJob

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
    deleted: Boolean = false,
    addToDocumentSetId: Option[Long] = None,
    lang: Option[String] = None,
    splitDocuments: Option[Boolean] = None,
    ocr: Option[Boolean] = None,
    nFiles: Option[Int] = None,
    nBytes: Option[Long] = None,
    nFilesProcessed: Option[Int] = None,
    nBytesProcessed: Option[Long] = None,
    estimatedCompletionTime: Option[Instant] = None,
    metadataJson: JsObject = JsObject(Seq())
  ): FileGroup

  def groupedFileUpload(
    id: Long = 0L,
    fileGroupId: Long = 0L,
    guid: UUID = new UUID(0L, 0L),
    contentType: String = "application/octet-stream",
    name: String = "filename.abc",
    documentMetadataJson: Option[JsObject] = None,
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
    url: String = "http://example.org",
    autocreate: Boolean = false,
    autocreateOrder: Int = 0
  ): Plugin

  def tag(
    id: Long = 0L,
    documentSetId: Long = 0L,
    name: String = "a tag",
    color: String = "abcdef"): Tag

  def tree(
    id: Long = 0L,
    documentSetId: Long = 0L,
    rootNodeId: Option[Long] = None,
    title: String = "title",
    documentCount: Option[Int] = None,
    lang: String = "en",
    description: String = "description",
    suppliedStopWords: String = "supplied stop words",
    importantWords: String = "important words",
    createdAt: Timestamp = now,
    tagId: Option[Long] = None,
    progress: Double = 1.0,
    progressDescription: String = ""
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
    text: String = "page text",
    isFromOcr: Boolean = false
  ): Page

  def file(
    id: Long = 0L,
    referenceCount: Int = 1,
    name: String = "filename",
    contentsLocation: String = "contents:location",
    contentsSize: Long = 2L,
    contentsSha1: Array[Byte] = Array(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19).map(_.toByte),
    viewLocation: String = "view:location",
    viewSize: Long = 3L
  ): File

  def upload(
    id: Long = 0L,
    userId: Long = 0L,
    guid: UUID = new UUID(0L, 0L),
    contentsOid: Long = 0L,
    uploadedFileId: Long = 0L,
    lastActivity: Timestamp = now,
    totalSize: Long = 0L
  ): Upload

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

  private def now: Timestamp = new Timestamp(scala.compat.Platform.currentTime)
}
