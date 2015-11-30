package com.overviewdocs.database

import org.specs2.mock.Mockito
import scala.concurrent.Future

import com.overviewdocs.test.DbSpecification
import com.overviewdocs.models.{ Document, DocumentSet, UploadedFile }
import com.overviewdocs.models.tables._
import com.overviewdocs.searchindex.IndexClient

class DocumentSetDeleterSpec extends DbSpecification with Mockito {

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
  }

  trait BaseScope extends DbScope {
    def numberOfDocuments = 3

    val mockIndexClient = smartMock[IndexClient]
    mockIndexClient.removeDocumentSet(any) returns Future.successful(())
    val deleter = new DocumentSetDeleter {
      override protected val indexClient = mockIndexClient
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
}
