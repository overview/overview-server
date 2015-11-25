package com.overviewdocs.database

import org.specs2.mock.Mockito
import scala.concurrent.Future

import com.overviewdocs.test.DbSpecification
import com.overviewdocs.models.{ Document, DocumentSet, UploadedFile }
import com.overviewdocs.models.tables._
import com.overviewdocs.searchindex.IndexClient

class DocumentSetDeleterSpec extends DbSpecification with Mockito {

  "DocumentSetDeleter" should {

    "delete documents, document_set_user, and document_set" in new BasicDocumentSetScope {
      deleteDocumentSet

      findDocumentSet(documentSet.id) must beEmpty
    }

    "delete the DocumentSet from ElasticSearch" in new BasicDocumentSetScope {
      deleteDocumentSet

      there was one(mockIndexClient).removeDocumentSet(documentSet.id)
    }

    "delete jobs" in new BasicDocumentSetScope {
      val job = factory.documentSetCreationJob(documentSetId=documentSet.id)

      deleteDocumentSet

      import database.api._
      blockingDatabase.option(DocumentSetCreationJobs.filter(_.id === job.id)) must beNone
    }

    "delete trees" in new BasicDocumentSetScope {
      val node = factory.node()
      val tree = factory.tree(documentSetId=documentSet.id, rootNodeId=Some(node.id))

      deleteDocumentSet

      import database.api._
      blockingDatabase.option(Trees.filter(_.id === tree.id)) must beNone
    }

    "delete trees that have no nodes" in new BasicDocumentSetScope {
      val tree = factory.tree(documentSetId=documentSet.id)

      deleteDocumentSet

      import database.api._
      blockingDatabase.option(Trees.filter(_.id === tree.id)) must beNone
    }

    "delete csv imports" in new BasicDocumentSetScope {
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

    "delete user added data" in new BasicDocumentSetScope {
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
    
    "delete tree data" in new BasicDocumentSetScope {
      val node = factory.node(id = 1l, rootId = 1l)

      factory.nodeDocument(node.id, documents.head.id)
      factory.tree(documentSetId = documentSet.id, rootNodeId = Some(node.id))

      deleteDocumentSet

      findDocumentSet(documentSet.id) must beEmpty
    }

    "delete view data" in new BasicDocumentSetScope {
      val apiToken = factory.apiToken(documentSetId = Some(documentSet.id))
      val store = factory.store(apiToken = apiToken.token)
      val storeObject = factory.storeObject(storeId = store.id)
      documents.map(d => factory.documentStoreObject(documentId = d.id, storeObjectId = storeObject.id))
      factory.view(documentSetId = documentSet.id, apiToken = apiToken.token)

      deleteDocumentSet
      
      findDocumentSet(documentSet.id) must beEmpty
    }
  }

  trait BasicDocumentSetScope extends DbScope {
    def numberOfDocuments = 3

    val mockIndexClient = smartMock[IndexClient]
    mockIndexClient.removeDocumentSet(any) returns Future.successful(())
    val deleter = new DocumentSetDeleter {
      override protected val indexClient = mockIndexClient
    }

    val documentSet = createDocumentSet
    val documents = createDocuments

    factory.documentSetUser(documentSetId = documentSet.id)
    factory.documentProcessingError(documentSetId = documentSet.id)

    def deleteDocumentSet: Unit = await(deleter.delete(documentSet.id))

    def findDocumentSet(documentSetId: Long): Option[DocumentSet] = {
      import database.api._
      blockingDatabase.option(DocumentSets.filter(_.id === documentSetId))
    }

    def fileReferenceCount: Option[Int] = {
      import database.api._
      blockingDatabase.option(Files.map(_.referenceCount))
    }

    def createDocumentSet: DocumentSet = factory.documentSet()
    def createDocuments: Seq[Document] = Seq.fill(numberOfDocuments)(createDocument)
    def createDocument: Document = factory.document(documentSetId = documentSet.id)
  }

  trait FileUploadScope extends BasicDocumentSetScope {
    def refCount = 1
    
    override def createDocument = {
      val file = factory.file(referenceCount = refCount)
      factory.page(fileId = file.id, pageNumber = 1)

      factory.document(documentSetId = documentSet.id, fileId = Some(file.id))
    }

  }

  trait SplitFileUploadScope extends BasicDocumentSetScope {

    override def createDocuments = {
      val file = factory.file()
      val pages = Seq.tabulate(numberOfDocuments)(n => factory.page(fileId = file.id, pageNumber = n + 1))

      pages.map(p => factory.document(documentSetId = documentSet.id, fileId = Some(file.id),
        pageId = Some(p.id), pageNumber = Some(p.pageNumber)))
    }
  }
}
