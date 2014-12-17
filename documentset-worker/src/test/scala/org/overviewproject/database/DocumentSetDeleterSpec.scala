package org.overviewproject.database

import org.postgresql.util.PSQLException
import org.overviewproject.test._
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.{ Document, DocumentSet, UploadedFile }
import org.overviewproject.models.tables.{ Documents, DocumentSets, Files, Pages, UploadedFiles }
import java.sql.Timestamp
import scala.concurrent.Future
import org.overviewproject.test.factories.DbFactory

class DocumentSetDeleterSpec extends SlickSpecification {

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
    def numberOfDocuments = 10

    val deleter = new TestDocumentSetDeleter
    val documentSet = createDocumentSet

    val documents = createDocuments

    factory.documentSetUser(documentSetId = documentSet.id)
    factory.documentProcessingError(documentSetId = documentSet.id)

    def deleteDocumentSet = await { deleter.delete(documentSet.id) } must not(throwA[PSQLException])

    def findDocumentSet(documentSetId: Long)(implicit session: Session): Option[DocumentSet] = {
      val q = DocumentSets.filter(_.id === documentSetId)
      q.firstOption
    }

    def fileReferenceCount: Option[Int] = Files.map(_.referenceCount).firstOption

    def createDocumentSet: DocumentSet = factory.documentSet()
    def createDocuments: Seq[Document] = Seq.fill(numberOfDocuments)(createDocument)
    def createDocument: Document = factory.document(documentSetId = documentSet.id)
  }

  trait FileUploadScope extends BasicDocumentSetScope {
    override def createDocument = {
      val file = factory.file()
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

    def findUploadedFile(implicit session: Session): Option[UploadedFile] =
      UploadedFiles.firstOption

  }

  trait UserAddedDataScope extends BasicDocumentSetScope {
    val tag = factory.tag(documentSetId = documentSet.id)
    documents.foreach(d => factory.documentTag(d.id, tag.id))
    factory.searchResult(documentSetId = documentSet.id)
  }

  trait TreeScope extends BasicDocumentSetScope {
    val node = factory.node(id = 1l, rootId = 1l)

    factory.nodeDocument(node.id, documents.head.id)
    factory.tree(documentSetId = documentSet.id, rootNodeId = node.id)
  }
  
  trait PluginScope extends BasicDocumentSetScope {
    val apiToken = factory.apiToken(documentSetId = documentSet.id)
    val store = factory.store(apiToken = apiToken.token)
    val storeObject = factory.storeObject(storeId = store.id)
    documents.map(d => factory.documentStoreObject(documentId = d.id, storeObjectId = storeObject.id))
    factory.view(documentSetId = documentSet.id, apiToken = apiToken.token)

  }

  class TestDocumentSetDeleter(implicit val session: Session) extends DocumentSetDeleter with SlickClientInSession 
    
}
