package com.overviewdocs.util

import play.api.libs.json.JsObject

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models.DocumentDisplayMethod
import com.overviewdocs.test.DbSpecification

class SortedDocumentIdsRefresherSpec extends DbSpecification {
  "#refreshDocumentSet" should {
    trait RefreshScope extends DbScope {
      val refresher = SortedDocumentIdsRefresher
      val db = new DbMethods

      def refresh(documentSetId: Long): Unit = await(refresher.refreshDocumentSet(documentSetId))
    }

    "Do nothing if the document set does not exist" in new RefreshScope {
      db.createDocumentSet(2L) // a dummy document set: should not be changed
      db.setSortedDocumentIds(2L, Seq(2L, 3L, 4L))
      refresh(1L)
      db.getSortedDocumentIds(2L) must beEqualTo(Some(Seq(2L, 3L, 4L)))
    }

    "create an empty list when there are no documents" in new RefreshScope {
      db.createDocumentSet(1L)
      // Create dummy documents that should not be included
      db.createDocumentSet(2L)
      db.createDocument(2L, 3L, "", "", None)
      refresh(1L)
      db.getSortedDocumentIds(1L) must beEqualTo(Some(Seq()))
    }

    "create a list sorted by title" in new RefreshScope {
      db.createDocumentSet(1L)
      db.createDocument(1L, 2L, "c", "", None)
      db.createDocument(1L, 3L, "a", "", None)
      db.createDocument(1L, 4L, "b", "", None)
      refresh(1L)
      db.getSortedDocumentIds(1L) must beEqualTo(Some(Seq(3L, 4L, 2L)))
    }

    "sort by suppliedId if the title stays the same" in new RefreshScope {
      db.createDocumentSet(1L)
      db.createDocument(1L, 2L, "a", "b", None)
      db.createDocument(1L, 3L, "a", "a", None)
      db.createDocument(1L, 4L, "b", "", None)
      refresh(1L)
      db.getSortedDocumentIds(1L) must beEqualTo(Some(Seq(3L, 2L, 4L)))
    }

    "sort by pageNumber if title and suppliedId stay the same" in new RefreshScope {
      db.createDocumentSet(1L)
      db.createDocument(1L, 2L, "a", "b", Some(2))
      db.createDocument(1L, 3L, "a", "b", Some(1))
      db.createDocument(1L, 4L, "a", "a", None)
      db.createDocument(1L, 5L, "b", "", None)
      refresh(1L)
      db.getSortedDocumentIds(1L) must beEqualTo(Some(Seq(4L, 3L, 2L, 5L)))
    }
  }

  class DbMethods extends HasBlockingDatabase {
    import database.api._
    import com.overviewdocs.models.tables.{Documents,DocumentSets}
    import com.overviewdocs.models.{Document,DocumentSet}
    import slick.jdbc.GetResult

    def createDocumentSet(id: Long): DocumentSet = {
      val ret = DocumentSet(id, "", None, false, new java.sql.Timestamp(0L), 0, 0, 0, None, MetadataSchema.empty, false)
      blockingDatabase.run((DocumentSets returning DocumentSets).+=(ret))
    }

    def createDocument(documentSetId: Long, id: Long, title: String, suppliedId: String, pageNumber: Option[Int]): Document = {
      val ret = Document(
        id,
        documentSetId,
        None,
        suppliedId,
        title,
        pageNumber,
        Seq(),
        new java.sql.Timestamp(0L),
        None,
        None,
        DocumentDisplayMethod.auto,
        false,
        JsObject(Seq()),
        ""
      )
      blockingDatabase.run((Documents returning Documents).+=(ret))
    }

    def setSortedDocumentIds(documentSetId: Long, ids: Seq[Long]): Unit = {
      blockingDatabase.run(sqlu"""UPDATE document_set SET sorted_document_ids = $ids WHERE id = $documentSetId""")
    }

    def getSortedDocumentIds(documentSetId: Long): Option[Seq[Long]] = {
      blockingDatabase.option(sql"""
        SELECT sorted_document_ids
        FROM document_set
        WHERE id = $documentSetId
      """.as[Seq[Long]])
    }
  }
}
