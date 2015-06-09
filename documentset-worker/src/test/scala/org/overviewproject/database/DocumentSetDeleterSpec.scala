package org.overviewproject.database

import org.overviewproject.test.DbSpecification
import org.overviewproject.models.{ Document, DocumentSet, UploadedFile }
import org.overviewproject.models.tables.{ Documents, DocumentSets, Files, Pages, UploadedFiles }

class DocumentSetDeleterSpec extends DbSpecification {

  "DocumentSetDeleter" should {

    "delete documents, document_set_user, and document_set" in new BasicDocumentSetScope {
      deleteDocumentSet

      findDocumentSet(documentSet.id) must beEmpty
    }

    "delete csv uploads" in new CsvUploadScope {
      deleteDocumentSet

      findDocumentSet(documentSet.id) must beEmpty
      findUploadedFile must beEmpty
    }

    "delete user added data" in new UserAddedDataScope {
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

    "only decrement reference count to 0" in new InterruptedDeleteScope {
      deleteDocumentSet
      
      fileReferenceCount must beSome(0)
    }
    
    "delete tree data" in new TreeScope {
      deleteDocumentSet

      findDocumentSet(documentSet.id) must beEmpty
    }

    "delete view data" in new PluginScope {
      deleteDocumentSet
      
      findDocumentSet(documentSet.id) must beEmpty
    }
  }

  trait BasicDocumentSetScope extends DbScope {
    def numberOfDocuments = 3

    val deleter = new TestDocumentSetDeleter
    val documentSet = createDocumentSet

    val documents = createDocuments

    factory.documentSetUser(documentSetId = documentSet.id)
    factory.documentProcessingError(documentSetId = documentSet.id)

    def deleteDocumentSet = await { deleter.delete(documentSet.id) } must not(throwA[Exception])

    def findDocumentSet(documentSetId: Long): Option[DocumentSet] = {
      import databaseApi._
      blockingDatabase.option(DocumentSets.filter(_.id === documentSetId))
    }

    def fileReferenceCount: Option[Int] = {
      import databaseApi._
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

  trait CsvUploadScope extends BasicDocumentSetScope {

    override def createDocumentSet = {
      val uploadedFile = factory.uploadedFile(size = 100l)
      factory.documentSet(uploadedFileId = Some(uploadedFile.id))
    }

    def findUploadedFile: Option[UploadedFile] = {
      import databaseApi._
      blockingDatabase.option(UploadedFiles)
    }

  }

  trait UserAddedDataScope extends BasicDocumentSetScope {
    val tag = factory.tag(documentSetId = documentSet.id)
    documents.foreach(d => factory.documentTag(d.id, tag.id))
  }

  trait TreeScope extends BasicDocumentSetScope {
    val node = factory.node(id = 1l, rootId = 1l)

    factory.nodeDocument(node.id, documents.head.id)
    factory.tree(documentSetId = documentSet.id, rootNodeId = node.id)
  }
  
  trait PluginScope extends BasicDocumentSetScope {
    val apiToken = factory.apiToken(documentSetId = Some(documentSet.id))
    val store = factory.store(apiToken = apiToken.token)
    val storeObject = factory.storeObject(storeId = store.id)
    documents.map(d => factory.documentStoreObject(documentId = d.id, storeObjectId = storeObject.id))
    factory.view(documentSetId = documentSet.id, apiToken = apiToken.token)
  }

  trait InterruptedDeleteScope extends FileUploadScope {
    override def refCount = 0
  }
  
  class TestDocumentSetDeleter extends DocumentSetDeleter with DatabaseProvider
}
