/*
 * DocumentSetCleanerSpec.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */

package persistence

import anorm._
import anorm.SqlParser._
import helpers.DbSetup._
import helpers.DbSpecification
import org.specs2.mutable.Specification

class DocumentSetCleanerSpec extends DbSpecification {
  step(setupDb)
  
  "DocumentSetCleaner" should {

    trait DocumentSetContext extends DbTestContext {
      lazy val documentSetId = insertDocumentSet("DocumentSetCleanerSpec")
      var nodeId: Long = _
      var documentId: Long = _
      val cleaner = new DocumentSetCleaner

      override def setupWithDb = {
	nodeId = insertNode(documentSetId, None, "description")
	documentId = insertDocument(documentSetId, "title", "dcId")
	insertNodeDocument(nodeId, documentId)
      }
    }

    "delete node related data" in new DocumentSetContext {
      cleaner.clean(documentSetId)

      val noNodeDocument = SQL(
	"""
	  SELECT node_id FROM node_document WHERE node_id IN
	    (SELECT id FROM node WHERE document_set_id = {id})
	""").on("id" -> documentSetId).as(long("node_id") *).headOption
      noNodeDocument must beNone
      
      val noNode  = SQL("SELECT id FROM node WHERE id = {id}").on("id" -> nodeId).as(long("id") *).headOption
      noNode must beNone
    }
  }
  
  step(shutdownDb)
}
