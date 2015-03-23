package org.overviewproject.test.factories

import java.sql.{Connection,Timestamp}
import java.util.{Date,UUID}
import play.api.libs.json.JsObject
import scala.slick.jdbc.UnmanagedSession
import org.overviewproject.models.tables._
import org.overviewproject.models._
import org.overviewproject.tree.orm.{Document => DeprecatedDocument}
import scala.slick.lifted.AbstractTable


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
    createdAt: Date = new Date(1234L),
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
    createdAt,
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
    createdAt: Timestamp = new Timestamp(1234L),
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
    createdAt,
    fileId,
    pageId,
    pageNumber
  ).toDocument).toDeprecatedDocument

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
    deleted
  )

  override def documentSetUser(
    documentSetId: Long,
    userEmail: String,
    role: DocumentSetUser.Role
  ) = q.insertDocumentSetUser += podoFactory.documentSetUser(documentSetId, userEmail, role)
  
  override def documentTag(documentId: Long, tagId: Long) = {
    q.insertDocumentTag += podoFactory.documentTag(documentId, tagId)
  }

  override def documentStoreObject(
    documentId: Long = 0L,
    storeObjectId: Long = 0L,
    json: Option[JsObject] = None
  ) = q.insertDocumentStoreObject += podoFactory.documentStoreObject(
    documentId,
    storeObjectId,
    json
  )

  override def fileGroup(
    id: Long,
    userEmail: String,
    apiToken: Option[String],
    completed: Boolean,
    deleted: Boolean
  ) = q.insertFileGroup += podoFactory.fileGroup(id, userEmail, apiToken, completed, deleted)

  override def groupedFileUpload(
    id: Long,
    fileGroupId: Long,
    guid: UUID,
    contentType: String,
    name: String,
    size: Long,
    uploadedSize: Long,
    contentsOid: Long
  ) = q.insertGroupedFileUpload += podoFactory.groupedFileUpload(
    id,
    fileGroupId,
    guid,
    contentType,
    name,
    size,
    uploadedSize,
    contentsOid
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

  override def plugin(
    id: UUID = new UUID(0L, 0L),
    name: String = "name", 
    description: String = "description",
    url: String = "http://example.org"
  ) = q.insertPlugin += podoFactory.plugin(id, name, description, url)

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
  ) = q.insertTree += podoFactory.tree(
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
  )

  override def store(
    id: Long = 0L,
    apiToken: String = "token",
    json: JsObject = JsObject(Seq())
  ) = q.insertStore += podoFactory.store(id, apiToken, json)

  override def storeObject(
    id: Long = 0L,
    storeId: Long = 0L,
    indexedLong: Option[Long] = None,
    indexedString: Option[String] = None,
    json: JsObject = JsObject(Seq())
  ) = q.insertStoreObject += podoFactory.storeObject(
    id,
    storeId,
    indexedLong,
    indexedString,
    json
  )

  override def tag(
    id: Long = 0L,
    documentSetId: Long = 0L,
    name: String = "a tag",
    color: String = "abcdef"
  ) = q.insertTag += podoFactory.tag(id, documentSetId, name, color)

  override def view(
    id: Long = 0L,
    documentSetId: Long = 0L,
    url: String = "http://example.org",
    apiToken: String = "api-token",
    title: String = "title",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)
  ) = q.insertView += podoFactory.view(
    id,
    documentSetId,
    url,
    apiToken,
    title,
    createdAt
  )
  
  override def page(
    id: Long,
    fileId: Long,
    pageNumber: Int,
    dataLocation: String,
    dataSize: Long,
    data: Option[Array[Byte]],
    text: Option[String],
    dataErrorMessage: Option[String],
    textErrorMessage: Option[String]
  ) = q.insertPage += podoFactory.page(
    id, 
    fileId,
    pageNumber,
    dataLocation,
    dataSize,
    data,
    text,
    dataErrorMessage,
    textErrorMessage
  )  
  
  override def file(
    id: Long,
    referenceCount: Int,
    name: String,
    contentsLocation: String,
    contentsSize: Long,
    viewLocation: String,
    viewSize: Long
  ) = q.insertFile += podoFactory.file(
    id,
    referenceCount,
    name,
    contentsLocation,
    contentsSize,
    viewLocation,
    viewSize
  )
  
  override def uploadedFile(
    id: Long,
    contentDisposition: String,
    contentType: String,
    size: Long,
    uploadedAt: Timestamp 
  ) = q.insertUploadedFile += podoFactory.uploadedFile(id, contentDisposition, contentType, size, uploadedAt)
  
  override def documentProcessingError(
    id: Long,
    documentSetId: Long,
    textUrl: String,
    message: String,
    statusCode: Option[Int],
    headers: Option[String]
  ) = q.insertDocumentProcessingError += podoFactory.documentProcessingError(id, documentSetId, textUrl, message, statusCode, headers)
  

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
    fileGroupId: Option[Long],
    sourceDocumentSetId: Option[Long],
    treeTitle: Option[String],
    treeDescription: Option[String],
    tagId: Option[Long],
    state: DocumentSetCreationJobState.Value,
    fractionComplete: Double,
    statusDescription: String,
    canBeCancelled: Boolean
  ) = q.insertDocumentSetCreationJob += podoFactory.documentSetCreationJob(
    id, documentSetId, jobType, retryAttempts, lang, suppliedStopWords, importantWords, splitDocuments,
    documentcloudUsername, documentcloudPassword, contentsOid, fileGroupId,  sourceDocumentSetId,
    treeTitle, treeDescription, tagId, state, fractionComplete, statusDescription, canBeCancelled
  )

  override def documentSetCreationJobNode(
    documentSetCreationJobId: Long,
    nodeId: Long
  ) = q.insertDocumentSetCreationJobNode += podoFactory.documentSetCreationJobNode(documentSetCreationJobId, nodeId)
  
  
  override def tempDocumentSetFile(
     documentSetId: Long,
     fileId: Long
  ) = q.insertTempDocumentSetFile += podoFactory.tempDocumentSetFile(documentSetId, fileId)
    
}

object DbFactory {
  object queries {
    import org.overviewproject.database.Slick.simple._

    // Compile queries once, instead of once per test
    val insertApiToken = (ApiTokens returning ApiTokens).insertInvoker
    val insertDocument = (Documents returning Documents).insertInvoker
    val insertDocumentSet = (DocumentSets returning DocumentSets).insertInvoker
    val insertDocumentTag = (DocumentTags returning DocumentTags).insertInvoker
    val insertDocumentSetUser = (DocumentSetUsers returning DocumentSetUsers).insertInvoker
    val insertDocumentStoreObject = (DocumentStoreObjects returning DocumentStoreObjects).insertInvoker
    val insertNode = (Nodes returning Nodes).insertInvoker
    val insertNodeDocument = (NodeDocuments returning NodeDocuments).insertInvoker
    val insertPlugin = (Plugins returning Plugins).insertInvoker
    val insertStore = (Stores returning Stores).insertInvoker
    val insertStoreObject = (StoreObjects returning StoreObjects).insertInvoker
    val insertTree = (Trees returning Trees).insertInvoker
    val insertTag = (Tags returning Tags).insertInvoker
    val insertView = (Views returning Views).insertInvoker
    val insertPage = (Pages returning Pages).insertInvoker
    val insertFile = (Files returning Files).insertInvoker
    val insertFileGroup = (FileGroups returning FileGroups).insertInvoker
    val insertGroupedFileUpload = (GroupedFileUploads returning GroupedFileUploads).insertInvoker
    val insertUploadedFile = (UploadedFiles returning UploadedFiles).insertInvoker
    val insertDocumentProcessingError =  (DocumentProcessingErrors returning DocumentProcessingErrors).insertInvoker
    val insertDocumentSetCreationJob = (DocumentSetCreationJobs returning DocumentSetCreationJobs).insertInvoker
    val insertDocumentSetCreationJobNode = (DocumentSetCreationJobNodes returning DocumentSetCreationJobNodes).insertInvoker
    val insertTempDocumentSetFile = (TempDocumentSetFiles returning TempDocumentSetFiles).insertInvoker
  }
}
