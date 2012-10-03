package models

import java.sql.Connection
import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DocumentListLoaderSpec extends Specification with Mockito {

  "DocumentListLoaderSpec" should {
    class TestDocumentListLoader(loader: DocumentTagDataLoader, parser: DocumentListParser) extends DocumentListLoader(loader, parser) {
      def loadDocuments(documentIds: Seq[Long])(implicit c: Connection): Seq[core.Document] =
	loadDocumentList(documentIds)
    }
      
    
    
    trait MockComponents extends Scope {
      implicit val unusedConnection: Connection = null
      val documentIds = Seq(4l, 5l, 6l)
      val loader = mock[DocumentTagDataLoader]
      val parser = mock[DocumentListParser]

      val documentListLoader = new TestDocumentListLoader(loader, parser)
      
      val documentData = List((4l, "dummyTitle", "dummyId"))
      val documentTagData = List((4l, 6l))
      val documentNodeData = List((4l, 10l))

      loader loadDocuments (documentIds) returns documentData
      loader loadDocumentTags (documentIds) returns documentTagData
      loader loadNodes (documentIds) returns documentNodeData
    }

    "load documents, tags, nodes, and then parse results" in new MockComponents {
      documentListLoader.loadDocuments(documentIds)
      
      there was one(loader).loadDocuments(documentIds)
      there was one(loader).loadDocumentTags(documentIds)
      there was one(loader).loadNodes(documentIds)
      
      there was one(parser).createDocuments(documentData, documentTagData, documentNodeData)
    }
  }

}
