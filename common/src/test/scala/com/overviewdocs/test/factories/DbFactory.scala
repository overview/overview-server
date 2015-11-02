package com.overviewdocs.test.factories

import java.sql.Timestamp
import java.time.Instant
import java.util.{Date,UUID}
import play.api.libs.json.JsObject

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models._
import com.overviewdocs.models.tables._

/** Creates objects in the database while returning them.
  *
  * Pass `0L` into any primary key column to have DbFactory select an ID
  * automatically.
  *
  * @see Factory
  */
object DbFactory extends Factory with HasBlockingDatabase {
  import database.api._

  private val podoFactory = PodoFactory

  private def run[T](f: DBIO[T]): T = blockingDatabase.run(f)

  override def apiToken(
    token: String,
    createdAt: Timestamp,
    createdBy: String,
    description: String,
    documentSetId: Option[Long]
  ) = run(q.insertApiToken += podoFactory.apiToken(
    token,
    createdAt,
    createdBy,
    description,
    documentSetId
  ))

  override def document(
    id: Long,
    documentSetId: Long,
    url: Option[String],
    suppliedId: String,
    title: String,
    keywords: Seq[String],
    createdAt: Date,
    pageNumber: Option[Int],
    fileId: Option[Long],
    pageId: Option[Long],
    displayMethod: DocumentDisplayMethod.Value,
    isFromOcr: Boolean,
    metadataJson: JsObject,
    text: String
  ) = run(q.insertDocument += podoFactory.document(
    id,
    documentSetId,
    url,
    suppliedId,
    title,
    keywords,
    createdAt,
    pageNumber,
    fileId,
    pageId,
    displayMethod,
    isFromOcr,
    metadataJson,
    text
  ))

  override def documentSet(
    id: Long,
    title: String,
    query: Option[String],
    isPublic: Boolean,
    createdAt: Timestamp,
    documentCount: Int,
    documentProcessingErrorCount: Int,
    importOverflowCount: Int,
    uploadedFileId: Option[Long],
    metadataSchema: MetadataSchema,
    deleted: Boolean
  ) = run(q.insertDocumentSet += podoFactory.documentSet(
    id,
    title,
    query,
    isPublic,
    createdAt,
    documentCount,
    documentProcessingErrorCount,
    importOverflowCount,
    uploadedFileId,
    metadataSchema,
    deleted
  ))

  override def documentSetUser(
    documentSetId: Long,
    userEmail: String,
    role: DocumentSetUser.Role
  ) = run(q.insertDocumentSetUser += podoFactory.documentSetUser(documentSetId, userEmail, role))
  
  override def documentTag(documentId: Long, tagId: Long) = {
    run(q.insertDocumentTag += podoFactory.documentTag(documentId, tagId))
  }

  override def documentStoreObject(
    documentId: Long = 0L,
    storeObjectId: Long = 0L,
    json: Option[JsObject] = None
  ) = run(q.insertDocumentStoreObject += podoFactory.documentStoreObject(
    documentId,
    storeObjectId,
    json
  ))

  override def fileGroup(
    id: Long,
    userEmail: String,
    apiToken: Option[String],
    deleted: Boolean,
    addToDocumentSetId: Option[Long],
    lang: Option[String],
    splitDocuments: Option[Boolean],
    nFiles: Option[Int],
    nBytes: Option[Long],
    nFilesProcessed: Option[Int],
    nBytesProcessed: Option[Long],
    estimatedCompletionTime: Option[Instant]
  ) = run(q.insertFileGroup += podoFactory.fileGroup(
    id,
    userEmail,
    apiToken,
    deleted,
    addToDocumentSetId,
    lang,
    splitDocuments,
    nFiles,
    nBytes,
    nFilesProcessed,
    nBytesProcessed,
    estimatedCompletionTime
  ))

  override def groupedFileUpload(
    id: Long,
    fileGroupId: Long,
    guid: UUID,
    contentType: String,
    name: String,
    size: Long,
    uploadedSize: Long,
    contentsOid: Long
  ) = run(q.insertGroupedFileUpload += podoFactory.groupedFileUpload(
    id,
    fileGroupId,
    guid,
    contentType,
    name,
    size,
    uploadedSize,
    contentsOid
  ))

  override def node(
    id: Long = 0L,
    rootId: Long = 0L,
    parentId: Option[Long] = None,
    description: String = "",
    cachedSize: Int = 0,
    isLeaf: Boolean = true
  ) = run(q.insertNode += podoFactory.node(
    id,
    rootId,
    parentId,
    description,
    cachedSize,
    isLeaf
  ))

  override def nodeDocument(nodeId: Long, documentId: Long) = {
    run(q.insertNodeDocument += podoFactory.nodeDocument(nodeId, documentId))
  }

  override def plugin(
    id: UUID,
    name: String,
    description: String,
    url: String,
    autocreate: Boolean,
    autocreateOrder: Int
  ) = run(q.insertPlugin += podoFactory.plugin(id, name, description, url, autocreate, autocreateOrder))

  override def tree(
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
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)
  ) = run(q.insertTree += podoFactory.tree(
    id,
    documentSetId,
    rootNodeId,
    jobId,
    title,
    documentCount,
    lang,
    description,
    suppliedStopWords,
    importantWords,
    createdAt
  ))

  override def store(
    id: Long = 0L,
    apiToken: String = "token",
    json: JsObject = JsObject(Seq())
  ) = run(q.insertStore += podoFactory.store(id, apiToken, json))

  override def storeObject(
    id: Long = 0L,
    storeId: Long = 0L,
    indexedLong: Option[Long] = None,
    indexedString: Option[String] = None,
    json: JsObject = JsObject(Seq())
  ) = run(q.insertStoreObject += podoFactory.storeObject(
    id,
    storeId,
    indexedLong,
    indexedString,
    json
  ))

  override def tag(
    id: Long = 0L,
    documentSetId: Long = 0L,
    name: String = "a tag",
    color: String = "abcdef"
  ) = run(q.insertTag += podoFactory.tag(id, documentSetId, name, color))

  override def view(
    id: Long = 0L,
    documentSetId: Long = 0L,
    url: String = "http://example.org",
    apiToken: String = "api-token",
    title: String = "title",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)
  ) = run(q.insertView += podoFactory.view(
    id,
    documentSetId,
    url,
    apiToken,
    title,
    createdAt
  ))

  override def page(
    id: Long,
    fileId: Long,
    pageNumber: Int,
    dataLocation: String,
    dataSize: Long,
    text: String,
    isFromOcr: Boolean
  ) = run(q.insertPage += podoFactory.page(
    id, 
    fileId,
    pageNumber,
    dataLocation,
    dataSize,
    text,
    isFromOcr
  ))

  override def file(
    id: Long,
    referenceCount: Int,
    name: String,
    contentsLocation: String,
    contentsSize: Long,
    contentsSha1: Array[Byte],
    viewLocation: String,
    viewSize: Long
  ) = run(q.insertFile += podoFactory.file(
    id,
    referenceCount,
    name,
    contentsLocation,
    contentsSize,
    contentsSha1,
    viewLocation,
    viewSize
  ))

  override def uploadedFile(
    id: Long,
    contentDisposition: String,
    contentType: String,
    size: Long,
    uploadedAt: Timestamp 
  ) = run(q.insertUploadedFile += podoFactory.uploadedFile(id, contentDisposition, contentType, size, uploadedAt))

  override def upload(
    id: Long,
    userId: Long,
    guid: UUID,
    contentsOid: Long,
    uploadedFileId: Long,
    lastActivity: Timestamp,
    totalSize: Long
  ) = run(q.insertUpload += podoFactory.upload(
    id,
    userId,
    guid,
    contentsOid,
    uploadedFileId,
    lastActivity,
    totalSize
  ))

  override def documentProcessingError(
    id: Long,
    documentSetId: Long,
    textUrl: String,
    message: String,
    statusCode: Option[Int],
    headers: Option[String]
  ) = run(q.insertDocumentProcessingError += podoFactory.documentProcessingError(
    id,
    documentSetId,
    textUrl,
    message,
    statusCode,
    headers
  ))

  override def documentSetCreationJob(
    id: Long,
    documentSetId: Long,
    jobType: DocumentSetCreationJobType.Value,
    retryAttempts: Int,
    lang: String,
    suppliedStopWords: String,
    importantWords: String,
    splitDocuments: Boolean,
    documentcloudUsername: Option[String],
    documentcloudPassword: Option[String],
    contentsOid: Option[Long],
    sourceDocumentSetId: Option[Long],
    treeTitle: Option[String],
    treeDescription: Option[String],
    tagId: Option[Long],
    state: DocumentSetCreationJobState.Value,
    fractionComplete: Double,
    statusDescription: String,
    canBeCancelled: Boolean
  ) = run(q.insertDocumentSetCreationJob += podoFactory.documentSetCreationJob(
    id, documentSetId, jobType, retryAttempts, lang, suppliedStopWords, importantWords, splitDocuments,
    documentcloudUsername, documentcloudPassword, contentsOid, sourceDocumentSetId,
    treeTitle, treeDescription, tagId, state, fractionComplete, statusDescription, canBeCancelled
  ))

  override def documentSetCreationJobNode(
    documentSetCreationJobId: Long,
    nodeId: Long
  ) = run(q.insertDocumentSetCreationJobNode += podoFactory.documentSetCreationJobNode(
    documentSetCreationJobId,
    nodeId
  ))

  override def tempDocumentSetFile(
     documentSetId: Long,
     fileId: Long
  ) = run(q.insertTempDocumentSetFile += podoFactory.tempDocumentSetFile(documentSetId, fileId))

  object q {
    // Compile queries once, instead of once per test
    val insertApiToken = (ApiTokens returning ApiTokens)
    val insertDocument = (Documents returning Documents)
    val insertDocumentSet = (DocumentSets returning DocumentSets)
    val insertDocumentTag = (DocumentTags returning DocumentTags)
    val insertDocumentSetUser = (DocumentSetUsers returning DocumentSetUsers)
    val insertDocumentStoreObject = (DocumentStoreObjects returning DocumentStoreObjects)
    val insertNode = (Nodes returning Nodes)
    val insertNodeDocument = (NodeDocuments returning NodeDocuments)
    val insertPlugin = (Plugins returning Plugins)
    val insertStore = (Stores returning Stores)
    val insertStoreObject = (StoreObjects returning StoreObjects)
    val insertTree = (Trees returning Trees)
    val insertTag = (Tags returning Tags)
    val insertView = (Views returning Views)
    val insertPage = (Pages returning Pages)
    val insertFile = (Files returning Files)
    val insertFileGroup = (FileGroups returning FileGroups)
    val insertGroupedFileUpload = (GroupedFileUploads returning GroupedFileUploads)
    val insertUpload = (Uploads returning Uploads)
    val insertUploadedFile = (UploadedFiles returning UploadedFiles)
    val insertDocumentProcessingError =  (DocumentProcessingErrors returning DocumentProcessingErrors)
    val insertDocumentSetCreationJob = (DocumentSetCreationJobs returning DocumentSetCreationJobs)
    val insertDocumentSetCreationJobNode = (DocumentSetCreationJobNodes returning DocumentSetCreationJobNodes)
    val insertTempDocumentSetFile = (TempDocumentSetFiles returning TempDocumentSetFiles)
  }
}
