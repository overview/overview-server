package org.overviewproject.test.factories

import java.sql.Timestamp
import java.util.{Date,UUID}
import play.api.libs.json.JsObject
import scala.util.Random

import org.overviewproject.metadata.MetadataSchema
import org.overviewproject.models._

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
      math.abs(random.nextLong)
    } else {
      idOr0
    }
  }

  private def getId(idOr0: UUID): UUID = {
    if (idOr0.equals(new UUID(0L, 0L))) {
      UUID.randomUUID()
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

  override def apiToken(
    token: String,
    createdAt: Timestamp,
    createdBy: String,
    description: String,
    documentSetId: Option[Long]
  ) = ApiToken(
    token,
    createdAt,
    createdBy,
    description,
    documentSetId
  )

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
    metadataJson: JsObject,
    text: String
  ) = Document(
    getId(id),
    getId(documentSetId),
    url,
    suppliedId,
    title,
    pageNumber,
    keywords,
    createdAt,
    fileId,
    pageId,
    displayMethod,
    metadataJson,
    text
  )

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
    metadataSchema,
    deleted
  )

  override def documentSetUser(
    documentSetId: Long,
    userEmail: String,
    role: DocumentSetUser.Role  
  ) = DocumentSetUser(documentSetId, userEmail, role)
    
  override def documentTag(
    documentId: Long,
    tagId: Long
  ) = DocumentTag(documentId, tagId)

  override def documentStoreObject(
    documentId: Long = 0L,
    storeObjectId: Long = 0L,
    json: Option[JsObject] = None
  ) = DocumentStoreObject(
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
  ) = FileGroup(getId(id), userEmail, apiToken, completed, deleted)

  override def groupedFileUpload(
    id: Long,
    fileGroupId: Long,
    guid: UUID,
    contentType: String,
    name: String,
    size: Long,
    uploadedSize: Long,
    contentsOid: Long
  ) = GroupedFileUpload(
    getId(id),
    getId(fileGroupId),
    getId(guid),
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
  ) = {
    val realId = getId(id)
    val realRootId = if (rootId == 0) realId else rootId
    Node(
      realId,
      realRootId,
      parentId,
      description,
      cachedSize,
      isLeaf
    )
  }

  override def nodeDocument(
    nodeId: Long,
    documentId: Long
  ) = NodeDocument(nodeId, documentId)

  override def plugin(
    id: UUID,
    name: String,
    description: String,
    url: String,
    autocreate: Boolean,
    autocreateOrder: Int
  ) = Plugin(getId(id), name, description, url, autocreate, autocreateOrder)

  override def store(
    id: Long = 0L,
    apiToken: String = "token",
    json: JsObject = JsObject(Seq())
  ) = Store(
    getId(id),
    apiToken,
    json
  )

  override def storeObject(
    id: Long = 0L,
    storeId: Long = 0L,
    indexedLong: Option[Long] = None,
    indexedString: Option[String] = None,
    json: JsObject = JsObject(Seq())
  ) = StoreObject(
    getId(id),
    getId(storeId),
    indexedLong,
    indexedString,
    json
  )

  override def tag(
    id: Long = 0L,
    documentSetId: Long = 0L,
    name: String = "a tag",
    color: String = "abcdef"
  ) = Tag(
    id=getId(id),
    documentSetId=getId(documentSetId),
    name=name,
    color=color
  )

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
  ) = Tree(
    id=getId(id),
    documentSetId=getId(documentSetId),
    rootNodeId=getId(rootNodeId),
    jobId=getId(jobId),
    title=title,
    documentCount=documentCount,
    lang=lang,
    description=description,
    suppliedStopWords=suppliedStopWords,
    importantWords=importantWords,
    createdAt=createdAt
  )

  override def view(
    id: Long = 0L,
    documentSetId: Long = 0L,
    url: String = "http://example.org",
    apiToken: String = "api-token",
    title: String = "title",
    createdAt: Timestamp = new Timestamp(0L)
  ) = View(
    getId(id),
    getId(documentSetId),
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
  ) = Page(
    getId(id),
    getId(fileId), 
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
    contentsSha1: Array[Byte],
    viewLocation: String,
    viewSize: Long
  ) = File(
    getId(id),
    referenceCount,
    name,
    contentsLocation,
    contentsSize,
    contentsSha1,
    viewLocation,
    viewSize
  )

  override def upload(
    id: Long,
    userId: Long,
    guid: UUID,
    contentsOid: Long,
    uploadedFileId: Long,
    lastActivity: Timestamp,
    totalSize: Long
  ) = Upload(
    getId(id),
    getId(userId),
    getId(guid),
    contentsOid,
    uploadedFileId,
    lastActivity,
    totalSize
  )
  
  override def uploadedFile(
    id: Long,
    contentDisposition: String,
    contentType: String,
    size: Long,
    uploadedAt: Timestamp
  ) = UploadedFile(getId(id), contentDisposition, contentType, size, uploadedAt)
  
  override def documentProcessingError(
    id: Long,
    documentSetId: Long,
    textUrl: String,
    message: String,
    statusCode: Option[Int],
    headers: Option[String]
  ) = DocumentProcessingError(getId(id), documentSetId, textUrl, message, statusCode, headers)
  

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
  ) = DocumentSetCreationJob(getId(id), documentSetId, jobType, retryAttempts, lang, suppliedStopWords, importantWords,
      splitDocuments, documentcloudUsername, documentcloudPassword, contentsOid, fileGroupId, sourceDocumentSetId, 
      treeTitle, treeDescription, tagId, state, fractionComplete, statusDescription, canBeCancelled)
  
   override def documentSetCreationJobNode(
     documentSetCreationJobId: Long,
     nodeId: Long
   ) = DocumentSetCreationJobNode(documentSetCreationJobId, nodeId)
  
   override def tempDocumentSetFile(
     documentSetId: Long,
     fileId: Long
   ) = TempDocumentSetFile(documentSetId, fileId)
}
