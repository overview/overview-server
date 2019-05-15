package com.overviewdocs.database

import org.specs2.mock.Mockito
import scala.concurrent.Future

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.{BlobStorageRef,Document,DocumentSet,File2,UploadedFile}
import com.overviewdocs.models.tables._
import com.overviewdocs.searchindex.IndexClient
import com.overviewdocs.test.DbSpecification

class DocumentSetDeleterSpec extends DbSpecification with Mockito {
  trait BaseScope extends DbScope {
    implicit protected val ec = database.executionContext
    def numberOfDocuments = 3

    val mockIndexClient = mock[IndexClient]
    val mockBlobStorage = mock[BlobStorage]
    mockBlobStorage.deleteMany(any) returns Future.unit
    mockIndexClient.removeDocumentSet(any) returns Future.unit
    val deleter = new DocumentSetDeleter {
      override protected val indexClient = mockIndexClient
      override protected val blobStorage = mockBlobStorage
    }

    val documentSet = factory.documentSet()
    val documents = createDocuments

    def deleteDocumentSet: Unit = await(deleter.delete(documentSet.id))

    def findDocumentSet(documentSetId: Long): Option[DocumentSet] = {
      import database.api._
      blockingDatabase.option(DocumentSets.filter(_.id === documentSetId))
    }

    def fileReferenceCount: Option[Int] = {
      import database.api._
      blockingDatabase.option(Files.map(_.referenceCount))
    }

    def findFile2(file2Id: Long): Option[File2] = {
      import database.api._
      blockingDatabase.option(File2s.filter(_.id === file2Id))
    }

    def createDocuments: Seq[Document] = Seq.fill(numberOfDocuments)(createDocument)
    def createDocument: Document = factory.document(documentSetId = documentSet.id)
  }

  trait FileUploadScope extends BaseScope {
    def refCount = 1
    
    override def createDocument = {
      val file = factory.file(referenceCount = refCount)
      factory.page(fileId = file.id, pageNumber = 1)

      factory.document(documentSetId = documentSet.id, fileId = Some(file.id))
    }

  }

  trait SplitFileUploadScope extends BaseScope {

    override def createDocuments = {
      val file = factory.file()
      val pages = Seq.tabulate(numberOfDocuments)(n => factory.page(fileId = file.id, pageNumber = n + 1))

      pages.map(p => factory.document(documentSetId = documentSet.id, fileId = Some(file.id),
        pageId = Some(p.id), pageNumber = Some(p.pageNumber)))
    }
  }

  "DocumentSetDeleter" should {

    "delete documents, document_set_user, and document_set" in new BaseScope {
      factory.documentSetUser(documentSetId = documentSet.id)
      factory.documentProcessingError(documentSetId = documentSet.id)

      deleteDocumentSet

      findDocumentSet(documentSet.id) must beEmpty
    }

    "delete the DocumentSet from ElasticSearch" in new BaseScope {
      deleteDocumentSet

      there was one(mockIndexClient).removeDocumentSet(documentSet.id)
    }

    "delete document_cloud_imports" in new BaseScope {
      val dci = factory.documentCloudImport(documentSetId=documentSet.id)
      factory.documentCloudImportIdList(documentCloudImportId=dci.id)

      deleteDocumentSet

      blockingDatabase.option(DocumentCloudImports) must beNone
      blockingDatabase.option(DocumentCloudImportIdLists) must beNone
    }

    "delete clone_jobs" in new BaseScope {
      val job = factory.cloneJob(destinationDocumentSetId=documentSet.id)

      deleteDocumentSet

      import database.api._
      blockingDatabase.option(CloneJobs.filter(_.id === job.id)) must beNone
    }

    "delete trees" in new BaseScope {
      val node = factory.node()
      val tree = factory.tree(documentSetId=documentSet.id, rootNodeId=Some(node.id))

      deleteDocumentSet

      import database.api._
      blockingDatabase.option(Trees.filter(_.id === tree.id)) must beNone
    }

    "delete document_id_list" in new BaseScope {
      val documentIdList = factory.documentIdList(documentSetId=documentSet.id.toInt)

      deleteDocumentSet

      import database.api._
      blockingDatabase.option(DocumentIdLists.filter(_.id === documentIdList.id)) must beNone
    }

    "delete trees that have no nodes" in new BaseScope {
      val tree = factory.tree(documentSetId=documentSet.id)

      deleteDocumentSet

      import database.api._
      blockingDatabase.option(Trees.filter(_.id === tree.id)) must beNone
    }

    "delete csv imports" in new BaseScope {
      import database.api._

      val loid: Long = blockingDatabase.run(database.largeObjectManager.create.transactionally)
      val csvImport = factory.csvImport(documentSetId=documentSet.id, loid=loid)
      deleteDocumentSet

      blockingDatabase.option(CsvImports.filter(_.id === csvImport.id)) must beNone

      {
        blockingDatabase.run((for {
          lo <- database.largeObjectManager.open(loid, LargeObject.Mode.Read)
          bytes <- lo.read(1)
        } yield bytes).transactionally)
      } must throwA[Exception]
    }

    "delete user added data" in new BaseScope {
      val tag = factory.tag(documentSetId = documentSet.id)
      documents.foreach(d => factory.documentTag(d.id, tag.id))

      deleteDocumentSet

      findDocumentSet(documentSet.id) must beEmpty
    }

    "decrement reference count in files and pages for uploaded files" in new FileUploadScope {
      deleteDocumentSet

      fileReferenceCount must beSome(0)
    }

    "decrement reference count when uploaded files are split into pages" in new SplitFileUploadScope {
      deleteDocumentSet

      fileReferenceCount must beSome(0)
    }
    
    "delete tree data" in new BaseScope {
      val node = factory.node(id = 1l, rootId = 1l)

      factory.nodeDocument(node.id, documents.head.id)
      factory.tree(documentSetId = documentSet.id, rootNodeId = Some(node.id))

      deleteDocumentSet

      findDocumentSet(documentSet.id) must beEmpty
    }

    "delete view data" in new BaseScope {
      val apiToken = factory.apiToken(documentSetId = Some(documentSet.id))
      val store = factory.store(apiToken = apiToken.token)
      val storeObject = factory.storeObject(storeId = store.id)
      documents.map(d => factory.documentStoreObject(documentId = d.id, storeObjectId = storeObject.id))
      factory.view(documentSetId = documentSet.id, apiToken = apiToken.token)

      deleteDocumentSet
      
      findDocumentSet(documentSet.id) must beEmpty
    }

    "delete file2s" in new BaseScope {
      val file2 = factory.file2(blob=Some(BlobStorageRef("foo", 10L)), thumbnailBlob=Some(BlobStorageRef("bar", 5L)))
      factory.documentSetFile2(documentSet.id, file2.id)

      deleteDocumentSet

      findFile2(file2.id) must beNone
      there was one(mockBlobStorage).deleteMany(Vector("foo", "bar"))
    }

    "delete child file2s" in new BaseScope {
      val rootFile2 = factory.file2()
      val childFile2 = factory.file2(rootFile2Id=Some(rootFile2.id), parentFile2Id=Some(rootFile2.id))
      factory.documentSetFile2(documentSet.id, rootFile2.id)

      deleteDocumentSet

      findFile2(childFile2.id) must beNone
    }

    "not delete shared file2s" in new BaseScope {
      val file2 = factory.file2()
      factory.documentSetFile2(documentSet.id, file2.id)
      factory.documentSetFile2(factory.documentSet().id, file2.id)

      deleteDocumentSet

      findFile2(file2.id) must beSome
    }
  }
}
