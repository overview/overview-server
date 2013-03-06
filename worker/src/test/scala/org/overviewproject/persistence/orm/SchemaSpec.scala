package org.overviewproject.persistence.orm

import org.overviewproject.test.DbSetup._
import org.overviewproject.test.DbSpecification 
import org.overviewproject.tree.orm.{ Document, Node }
import org.overviewproject.tree.orm.DocumentType._
import org.overviewproject.postgres.SquerylEntrypoint._
import scala.Option.option2Iterable

class SchemaSpec extends DbSpecification {
  
  "The Squeryl DB Schema" should {
    step(setupDb)
    
    "not autogenerate an id of document if one is given" in new DbTestContext {
      val documentSetId: Long = insertDocumentSet("SchemaSpec")
      val documentId:Long = (documentSetId << 32) | 1l
      
      val document = Document(DocumentCloudDocument, documentSetId, id = documentId, title = Some("title"), documentcloudId = Some("documentCloudId"))
      Schema.documents.insert(document)
      
      document.id must be equalTo(documentId)
    }
    
    "not autogenerate node id if one is given" in new DbTestContext {
      val documentSetId: Long = insertDocumentSet("SchemaSpec")
      val nodeId: Long = (documentSetId << 32) | 1l
      
      val node = Node(documentSetId, None, "node", 0, Array(), nodeId)
      Schema.nodes.insert(node)
      
      node.id must be equalTo(nodeId)
    }
    
    step(shutdownDb)
  }

}