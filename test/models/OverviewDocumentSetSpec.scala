package models

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start,stop}
import play.api.test.FakeApplication

// OverviewDocumentSet wraps models.orm.DocumentSet.
import models.orm.{DocumentSet,UploadedFile}
import models.orm.DocumentSetType._
import models.upload.OverviewUploadedFile

class OverviewDocumentSetSpec extends Specification {
  step(start(FakeApplication()))

  "OverviewDocumentSet" should {
    trait OneDocumentSet {
      def throwWrongType = throw new Exception("Wrong DocumentSet type")
      def ormDocumentSet: DocumentSet
      lazy val documentSet = OverviewDocumentSet(ormDocumentSet)
    }

    trait CsvImportDocumentSetScope extends Scope with OneDocumentSet {
      val ormUploadedFile = UploadedFile(
          id = 0L,
          uploadedAt = new java.sql.Timestamp(new java.util.Date().getTime()),
          contentsOid = 0L,
          contentDisposition = "attachment; filename=foo.csv",
          contentType = "text/csv; charset=latin1",
          size=0L
      )

      val title = "Title"
      val createdAt = new java.util.Date()
      val count = 10

      override def ormDocumentSet = DocumentSet(
          CsvImportDocumentSet,
          title = title,
          createdAt = new java.sql.Timestamp(createdAt.getTime()),
          uploadedFile = Some(ormUploadedFile),
          providedDocumentCount = Some(count)
      )
    }

    trait DocumentCloudDocumentSetScope extends Scope with OneDocumentSet {
      val title = "Title"
      val query = "Query"
      val createdAt = new java.util.Date()
      val count = 10

      override def ormDocumentSet = DocumentSet(
          DocumentCloudDocumentSet,
          title = title,
          query = Some(query),
          createdAt = new java.sql.Timestamp(createdAt.getTime()),
          providedDocumentCount = Some(count)
      )
    }

    "apply() should generate a CsvImportDocumentSet" in new CsvImportDocumentSetScope {
      documentSet must beAnInstanceOf[OverviewDocumentSet.CsvImportDocumentSet]
    }

    "apply() should generate a DocumentCloudDocumentSet" in new DocumentCloudDocumentSetScope {
      documentSet must beAnInstanceOf[OverviewDocumentSet.DocumentCloudDocumentSet]
    }

    "createdAt should point to the ORM document" in new CsvImportDocumentSetScope {
      documentSet.createdAt must beEqualTo(createdAt)
    }

    "title should be the title" in new DocumentCloudDocumentSetScope {
      documentSet.title must beEqualTo(title)
    }

    "documentCount should be the document count" in new DocumentCloudDocumentSetScope {
      documentSet.documentCount must beEqualTo(count)
    }

    "CSV document sets must have an uploadedFile" in new CsvImportDocumentSetScope {
      documentSet match {
        case csvDs: OverviewDocumentSet.CsvImportDocumentSet => {
          csvDs.uploadedFile must beAnInstanceOf[Some[OverviewUploadedFile]]
        }
        case _ => throwWrongType
      }
    }

    "DC document sets must have a query" in new DocumentCloudDocumentSetScope {
      documentSet match {
        case dcDs: OverviewDocumentSet.DocumentCloudDocumentSet => {
          dcDs.query must beEqualTo(query)
        }
        case _ => throwWrongType
      }
    }
  }

  step(stop)
}
