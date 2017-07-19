package com.overviewdocs.util

import org.specs2.mock.Mockito
import play.api.libs.json.JsObject
import scala.concurrent.Future

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models.{DocumentDisplayMethod,PdfNoteCollection}
import com.overviewdocs.searchindex.IndexClient
import com.overviewdocs.test.DbSpecification

class AddDocumentsCommonSpec extends DbSpecification with Mockito {
  trait BaseScope extends DbScope {
    val db = new DbMethods

    def doBefore(documentSetId: Long): Unit = await(subject.beforeAddDocuments(documentSetId))
    def doAfter(documentSetId: Long): Unit = await(subject.afterAddDocuments(documentSetId))

    val mockIndexClient = smartMock[IndexClient]
    val subject = new AddDocumentsCommon {
      override protected val indexClient = mockIndexClient
    }

    mockIndexClient.addDocumentSet(any) returns Future.unit
    mockIndexClient.refresh(any) returns Future.unit
  }

  "#beforeAddDocuments" should {
     "clear DocumentProcessingErrors in" in new BaseScope {
      db.createDocumentSet(1L) // dummy
      db.createDocumentProcessingError(1L)
      db.getNumDocumentProcessingErrors(1L) must beEqualTo(1) // tests the test!
      doBefore(1L)
      db.createDocument(1L, 2L, "", "", None)
      doAfter(1L)                                             // finish off for a quasi-integration test
      db.getNumDocumentProcessingErrors(1L) must beEqualTo(0)
    }
  }

  "#afterAddDocuments" should {
    trait AfterScope extends BaseScope {
    }

    "refresh the search index" in new AfterScope {
      db.createDocumentSet(1L) // dummy
      doAfter(1L)
      there was one(mockIndexClient).refresh(1L)
    }

    "do nothing if the document set does not exist" in new AfterScope {
      db.createDocumentSet(2L) // a dummy document set: should not be changed
      db.setSortedDocumentIds(2L, Seq(2L, 3L, 4L))
      doAfter(1L)
      db.getSortedDocumentIds(2L) must beEqualTo(Some(Seq(2L, 3L, 4L)))
    }

    "create an empty list when there are no documents" in new AfterScope {
      db.createDocumentSet(1L)
      // Create dummy documents that should not be included
      db.createDocumentSet(2L)
      db.createDocument(2L, 3L, "", "", None)
      doAfter(1L)
      db.getSortedDocumentIds(1L) must beEqualTo(Some(Seq()))
    }

    "create a list sorted by title" in new AfterScope {
      db.createDocumentSet(1L)
      db.createDocument(1L, 2L, "c", "", None)
      db.createDocument(1L, 3L, "a", "", None)
      db.createDocument(1L, 4L, "b", "", None)
      doAfter(1L)
      db.getSortedDocumentIds(1L) must beEqualTo(Some(Seq(3L, 4L, 2L)))
    }

    "delete cached DocumentIdLists" in new AfterScope {
      db.createDocumentSet(1L)
      db.createDocument(1L, 2L, "a", "b", None)
      db.createDocumentIdList(1L, "foo")
      doAfter(1L)
      db.getDocumentIdList(1L, "foo") must beNone
    }

    "sort by suppliedId if the title stays the same" in new AfterScope {
      db.createDocumentSet(1L)
      db.createDocument(1L, 2L, "a", "b", None)
      db.createDocument(1L, 3L, "a", "a", None)
      db.createDocument(1L, 4L, "b", "", None)
      doAfter(1L)
      db.getSortedDocumentIds(1L) must beEqualTo(Some(Seq(3L, 2L, 4L)))
    }

    "sort by pageNumber if title and suppliedId stay the same" in new AfterScope {
      db.createDocumentSet(1L)
      db.createDocument(1L, 2L, "a", "b", Some(2))
      db.createDocument(1L, 3L, "a", "b", Some(1))
      db.createDocument(1L, 4L, "a", "a", None)
      db.createDocument(1L, 5L, "b", "", None)
      doAfter(1L)
      db.getSortedDocumentIds(1L) must beEqualTo(Some(Seq(4L, 3L, 2L, 5L)))
    }
  }

  // Utilitity functions we need for these tests. Rather a lot unfortunately.
  class DbMethods extends HasBlockingDatabase {
    import database.api._
    import com.overviewdocs.models.tables.{Documents,DocumentIdLists,DocumentSets,DocumentProcessingErrors}
    import com.overviewdocs.models.{Document,DocumentIdList,DocumentSet,DocumentProcessingError}
    import slick.jdbc.GetResult

    def createDocumentSet(id: Long): DocumentSet = {
      val ret = DocumentSet(id, "", None, false, new java.sql.Timestamp(0L), 0, 0, 0, MetadataSchema.empty, false)
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
        None,
        PdfNoteCollection(Array()),
        ""
      )
      blockingDatabase.run((Documents returning Documents).+=(ret))
    }

    def createDocumentProcessingError(documentSetId: Long) : Unit = {
      val ret =  DocumentProcessingError(10L, documentSetId, "made up file", "made up error", None, None)
      blockingDatabase.run((DocumentProcessingErrors returning DocumentProcessingErrors).+=(ret))
    }

    def createDocumentIdList(documentSetId: Long, fieldName: String): Unit = {
      val ret = DocumentIdList(1L, documentSetId.toInt, fieldName, Array(1, 2, 3))
      blockingDatabase.run((DocumentIdLists returning DocumentIdLists).+=(ret))
    }

    def getDocumentIdList(documentSetId: Long, fieldName: String): Option[DocumentIdList] = {
      blockingDatabase.option(DocumentIdLists.filter(_.documentSetId === documentSetId.toInt).filter(_.fieldName === fieldName))
    }

    def getNumDocumentProcessingErrors(documentSetId: Long) : Long = {
      blockingDatabase.length(DocumentProcessingErrors.filter(_.documentSetId === documentSetId))
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
