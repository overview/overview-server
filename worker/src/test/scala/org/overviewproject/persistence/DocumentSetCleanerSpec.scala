/*
 * DocumentSetCleanerSpec.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */

package org.overviewproject.persistence

import java.sql.Connection
import anorm.{sqlToSimple, toParameterValue}
import anorm.SQL
import anorm.SqlParser.long
import org.overviewproject.test.DbSetup.{insertDocument, insertDocumentSet, insertNode, insertNodeDocument}
import org.overviewproject.test.DbSpecification

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

    def findNodeWithDocument(documentSetId: Long)(implicit c: Connection): Option[Long] =
      SQL("""
	    SELECT node_id FROM node_document WHERE node_id IN
	      (SELECT id FROM node WHERE document_set_id = {id})
          """).on("id" -> documentSetId).as(long("node_id") *).headOption

    def findNode(nodeId: Long)(implicit c: Connection): Option[Long] =
      SQL("SELECT id FROM node WHERE id = {id}").on("id" -> nodeId).as(long("id") *).headOption

    def findDocument(documentSetId: Long)(implicit c: Connection): Option[Long] =
      SQL("SELECT id FROM document WHERE document_set_id = {id}").
        on("id" -> documentSetId).as(long("id") *).headOption

    "delete node related data" in new DocumentSetContext {
      cleaner.clean(documentSetId)

      val noNodeDocument = findNodeWithDocument(documentSetId)
      noNodeDocument must beNone

      val noNode = findNode(nodeId)
      noNode must beNone
    }

    "delete document related data" in new DocumentSetContext {
      cleaner.clean(documentSetId)

      val noDocument = findDocument(documentSetId)

      noDocument must beNone
    }
  }

  step(shutdownDb)
}
