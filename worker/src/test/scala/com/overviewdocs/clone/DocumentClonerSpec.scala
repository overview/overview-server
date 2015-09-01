package com.overviewdocs.clone

import play.api.libs.json.Json

import com.overviewdocs.persistence.DocumentSetIdGenerator
import com.overviewdocs.models.{Document,DocumentSet,File}
import com.overviewdocs.models.tables.{Documents,Files}
import com.overviewdocs.test.DbSpecification

class DocumentClonerSpec extends DbSpecification {
  "DocumentCloner" should {

    trait BaseScope extends DbScope {
      import database.api._

      val originalDocumentSet: DocumentSet = factory.documentSet(id=1L)
      val cloneDocumentSet: DocumentSet = factory.documentSet(id=2L)

      def go = DocumentCloner.clone(originalDocumentSet.id, cloneDocumentSet.id)

      def findDocuments(documentSetId: Long): Seq[Document] = blockingDatabase.seq {
        Documents
          .filter(_.documentSetId === documentSetId)
          .sortBy(_.id)
      }

      def findAllFiles: Seq[File] = blockingDatabase.seq(Files.sortBy(_.id))
    }

    "with CSV-uploaded Documents" should {
      trait CsvUploadScope extends BaseScope {
        val originalDocuments: Seq[Document] = Seq(
          factory.document(
            id=(0x1L << 32) | 1L,
            documentSetId=originalDocumentSet.id,
            text="foo",
            metadataJson=Json.obj("foo" -> "bar")
          ),
          factory.document(
            id=(0x1L << 32) | 2L,
            documentSetId=originalDocumentSet.id,
            text="bar",
            metadataJson=Json.obj("bar" -> "baz")
          )
        )
      }

      "clone document text" in new CsvUploadScope {
        go
        findDocuments(cloneDocumentSet.id).map(_.text) must beEqualTo(originalDocuments.map(_.text))
      }

      "clone document metadata" in new CsvUploadScope {
        // https://www.pivotaltracker.com/story/show/99507728
        go
        findDocuments(cloneDocumentSet.id).map(_.metadataJson) must beEqualTo(originalDocuments.map(_.metadataJson))
      }

      "match clone document IDs to original document IDs" in new CsvUploadScope {
        go
        findDocuments(cloneDocumentSet.id).map(_.id & 0xffffffffL) must beEqualTo(originalDocuments.map(_.id & 0xffffffffL))
      }

      "encode the clone document set ID in the document IDs" in new CsvUploadScope {
        go
        findDocuments(cloneDocumentSet.id).map(_.id >> 32) must beEqualTo(Seq(cloneDocumentSet.id, cloneDocumentSet.id))
      }
    }

    "with uploaded-file Documents" should {
      trait UploadedFileScope extends BaseScope {
        val file1 = factory.file(id=1L) // split into pages
        val file2 = factory.file(id=2L) // whole
        val page11 = factory.page(fileId=file1.id, pageNumber=1)
        val page12 = factory.page(fileId=file1.id, pageNumber=2)

        val originalDocuments: Seq[Document] = Seq(
          factory.document(
            id=(0x1L << 32) | 1L,
            documentSetId=originalDocumentSet.id,
            text="foo",
            fileId=Some(file1.id),
            pageId=Some(page11.id),
            pageNumber=Some(1)
          ),
          factory.document(
            id=(0x1L << 32) | 2L,
            documentSetId=originalDocumentSet.id,
            text="bar",
            fileId=Some(file1.id),
            pageId=Some(page12.id),
            pageNumber=Some(2)
          ),
          factory.document(
            id=(0x1L << 32) | 3L,
            documentSetId=originalDocumentSet.id,
            text="baz",
            fileId=Some(file2.id)
          )
        )
      }

      "refer to the same Files" in new UploadedFileScope {
        go
        findDocuments(cloneDocumentSet.id).map(_.fileId) must beEqualTo(originalDocuments.map(_.fileId))
      }

      "refer to the same Pages" in new UploadedFileScope {
        go
        findDocuments(cloneDocumentSet.id).map(_.pageId) must beEqualTo(originalDocuments.map(_.pageId))
      }

      "copy the pageNumbers" in new UploadedFileScope {
        go
        findDocuments(cloneDocumentSet.id).map(_.pageNumber) must beEqualTo(originalDocuments.map(_.pageNumber))
      }

      "increment File refcounts" in new UploadedFileScope {
        val otherFile = factory.file(id=3L) // this one should *not* be incremented
        go
        findAllFiles.map(_.referenceCount) must beEqualTo(Seq(2, 2, 1))
      }
    }
  }
}
