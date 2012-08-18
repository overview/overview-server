/*
 * NodeDocumentBatchInserter.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import anorm.SqlParser._
import helpers.{DbSpecification, DbTestContext}
import java.sql.Connection

class NodeDocumentBatchInserterSpec extends DbSpecification {

  step(setupDB)
  
  trait DocumentsSetup extends DbTestContext {
    lazy val documentSetId = insertDocumentSet("NodeDocumentBatchInserterSpec")
    lazy val nodeId = insertNode(documentSetId)
  }
  
  private def failInsert = { throw new Exception("failed insert") }

  
  def insertDocumentSet(query: String)(implicit c: Connection): Long = {
    SQL("""
          INSERT INTO document_set (query) 
          VALUES('NodeWriterSpec')
        """).executeInsert().getOrElse(failInsert)
  }

  
  def insertNode(documentSetId: Long)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO node (description, document_set_id) VALUES 
        ('description', {documentSetId})
        """).on("documentSetId" -> documentSetId).executeInsert().getOrElse(failInsert)
  }

  def insertDocument(documentSetId: Long,
    title: String, textUrl: String, viewUrl: String)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document(document_set_id, title, text_url, view_url) VALUES 
          ({documentSetId}, {title}, {textUrl}, {viewUrl})
        """).on("documentSetId" -> documentSetId,
      "title" -> title, "textUrl" -> textUrl, "viewUrl" -> viewUrl).
      executeInsert().getOrElse(failInsert)
  }
  
  def insertDocuments(documentSetId: Long, count: Int)
                     (implicit c: Connection): Seq[Long] = {
    for (i <- 1 to count) yield 
      insertDocument(documentSetId, "title-" + i, "textUrl-" + i, "viewUrl-" + i)
  }
  
  def findNodeDocuments(implicit c: Connection) : Seq[(Long, Long)] = {
	SQL("SELECT node_id, document_id FROM node_document").
	   as(long("node_id") ~ long("document_id") map(flatten) *)
  }
  
  "NodeDocumentBatchInserter" should {
    
    "insert data after threshold is reached" in new DocumentsSetup {
      val documentIds = insertDocuments(documentSetId, 5)
      val threshold = 5
      val inserter = new NodeDocumentBatchInserter(threshold)
      
      documentIds.take(threshold - 1).foreach(inserter.insert(nodeId, _))
      
      val beforeThresholdReached = findNodeDocuments
      beforeThresholdReached must be empty
      
      documentIds.slice(threshold - 1, threshold).foreach(inserter.insert(nodeId, _))
      
      val afterThreshold = findNodeDocuments
      val expectedNodeDocuments = documentIds.map((nodeId, _))
      
      afterThreshold must haveTheSameElementsAs(expectedNodeDocuments)
    }
    
    "reset count after inserting data" in new DocumentsSetup {
      val documentIds = insertDocuments(documentSetId, 10)
      val firstBatch = documentIds.take(5)
      val secondBatch = documentIds.drop(5)
      val threshold = 5
      val inserter = new NodeDocumentBatchInserter(threshold)
      
      firstBatch.foreach(inserter.insert(nodeId, _))
      secondBatch.take(2).foreach(inserter.insert(nodeId, _))
      
      val firstBatchInserted = findNodeDocuments
      firstBatchInserted must have size(threshold)
      
      secondBatch.drop(2).foreach(inserter.insert(nodeId, _))
      
      val allInserted = findNodeDocuments
      val expectedNodeDocuments = documentIds.map((nodeId, _))
      
      allInserted must haveTheSameElementsAs(expectedNodeDocuments)
      
    }
    
    "flush remaining data when instructed to" in new DocumentsSetup {
      val documentIds = insertDocuments(documentSetId, 4)
      val threshold = 5
      val inserter = new NodeDocumentBatchInserter(threshold)
      
      documentIds.foreach(inserter.insert(nodeId, _))
      inserter.flush
      
      val flushedDocuments = findNodeDocuments
      val expectedNodeDocuments = documentIds.map((nodeId, _))
      
      flushedDocuments must haveTheSameElementsAs(expectedNodeDocuments)
    }
    
    "flush with no queued inserts doesn't fail" in new DocumentsSetup {
      val threshold = 5
      val inserter = new NodeDocumentBatchInserter(threshold)
      
      inserter.flush 
      "did not crash" must be equalTo("did not crash")
    }
  }
  
  step(shutdownDB)
}