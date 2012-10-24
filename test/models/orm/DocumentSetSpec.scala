package models.orm

import anorm._
import anorm.SqlParser._
import java.util.Date
import org.specs2.specification.Scope
import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode._
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }

import testutil.DbSetup._
import helpers.DbTestContext
import models.orm._

class DocumentSetSpec extends Specification {

  step(start(FakeApplication()))
  
  "DocumentSet" should {
    
    // Need inExample because Squeryl messes up implicit conversion
    inExample("create a DocumentSetCreationJob") in new DbTestContext {
      val query = "query"
      val title = "title"

      val documentSet = Schema.documentSets.insert(DocumentSet(0L, title, query))

      val job = documentSet.createDocumentSetCreationJob()
      
      job.documentSet must be equalTo(documentSet)

      val returnedJob = Schema.documentSetCreationJobs.single
      
      val returnedSet = Schema.documentSets.where(ds => ds.query === query).single
      returnedSet.withCreationJob.documentSetCreationJob must beEqualTo(Some(returnedJob))
    }

    inExample("set createdAt to the current date by default") in new Scope {
      val documentSet = DocumentSet(0L)
      documentSet.createdAt.getTime must beCloseTo((new Date().getTime), 1000)
    }
    
    inExample("throw exception if job creation is attempted before db insertion") in new DbTestContext {
      val query = "query"
      val title = "title"
      val documentSet = new DocumentSet(0L, title, query)
      
      documentSet.createDocumentSetCreationJob() must throwAn[IllegalArgumentException]
    }
    
    inExample("delete all references in other tables") in new DbTestContext {
      val query = "query"
      val documentSet = Schema.documentSets.insert(DocumentSet(0L, query))
      val id = documentSet.id
      val documentSetCreationJob = documentSet.createDocumentSetCreationJob()
      
      SQL("""
          INSERT INTO document_set_user (document_set_id, user_id)
          VALUES ({documentSetId}, 1)
          """).on("documentSetId" -> id).executeInsert()
   
      SQL("""
          INSERT INTO log_entry (document_set_id, user_id, date, component, action, details)
          VALUES ({documentSetId}, {userId}, '2012-08-20 09:12:12', 'component', 'action', 'details')
          """).on("documentSetId" -> id, "userId" -> 1).executeInsert()
      val tagId = insertTag(id, "tag")
      val nodeIds = insertNodes(id, 1)
      val documentIds = insertDocumentsForeachNode(id, nodeIds, 1)
      tagDocuments(tagId, documentIds)
      
      DocumentSet.delete(documentSet.id)
      
      def documentSetEntries(documentSetId: Long, table: String): List[Long] = {
        SQL("SELECT id FROM " + table + " WHERE document_set_id = {documentSetId}").
          on("documentSetId" -> id).as(scalar[Long] *)
      }
      def allEntries(table: String): List[(Long, Long)] = {
        SQL("SELECT * FROM " + table).as(scalar[Long] ~ scalar[Long] map(flatten) *)  
      }
      
      val logEntry = documentSetEntries(id, "log_entry") 
      logEntry must be empty
      
      val tagEntry = documentSetEntries(id, "tag") 
      tagEntry must be empty
      
      val documentTagEntries = allEntries("document_tag")
      documentTagEntries must be empty
      
      val nodeEntry = documentSetEntries(id, "node") 
      nodeEntry must be empty
      
      val documentEntry = documentSetEntries(id, "document")
      documentEntry must be empty
      
      val nodeDocumentEntries = allEntries("node_document")
      nodeDocumentEntries must be empty
      
      val documentSetUserEntries = allEntries("document_set_user")
      documentSetUserEntries must be empty
      
      val documentSetCreationJobEntries = documentSetEntries(id, "document_set_creation_job")
      documentSetCreationJobEntries must be empty
      
      val documentSetEntries = allEntries("document_set")
      documentSetEntries must be empty
      
      
    }
  }
  
  step(stop)
}
