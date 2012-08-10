/*
 * NodeWriterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package writers

import anorm._
import anorm.SqlParser._
import clustering.DocTreeNode
import clustering.ClusterTypes.DocumentID
import helpers.DbTestContext
import org.specs2.mutable.Specification
import scala.collection.mutable.Set


class NodeWriterSpec extends Specification {

  "NodeWriter" should {
    
    "insert root node with description, document set, and no parent" in new DbTestContext {
      
      val documentSetId = SQL("""
          INSERT INTO document_set(id, query) 
          VALUES(nextval('document_set_seq'), 'NodeWriterSpec')
          """).executeInsert().getOrElse(throw new Exception("failed insert"))
          
      val root = new DocTreeNode(Set[DocumentID]())
      val description = "description"
      root.description = description
      
      val writer = new NodeWriter(documentSetId)
      
      writer.write(root)
      
      val result = 
        SQL("SELECT id, description, parent_id, document_set_id FROM node").
      as(long("id") ~ str("description") ~ get[Option[Long]]("parent_id") ~ long("document_set_id")
          map(flatten) singleOpt)
                      
      result must beSome
      val (id, rootDescription, parentId, rootDocumentSetId) = result.get
      
      rootDescription must be equalTo(description)
      parentId must beNone
      rootDocumentSetId must be equalTo(documentSetId)
    }
  }
}