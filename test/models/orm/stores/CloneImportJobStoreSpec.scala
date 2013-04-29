package models.orm.stores

import java.sql.Timestamp
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start, stop}
import play.api.test.FakeApplication

import org.overviewproject.tree.orm.UploadedFile
import helpers.DbTestContext
import models.orm.DocumentSet
import models.orm.finders.{ UploadedFileFinder }

/** FIXME: move all this logic to the worker. The server should not deal with
  * any of this.
  */
class CloneImportJobStoreSpec extends Specification {
  step(start(FakeApplication()))

  trait BaseScope extends DbTestContext {
  }

  "CloneImportJobStoreSpec" should {
    "set isPublic=false on a clone DocumentSet" in new BaseScope {
      val documentSet = CloneImportJobStore.insertCloneOf(DocumentSet(isPublic=true))
      documentSet.isPublic must beEqualTo(false)
    }

    "set createdAt to the current date on a clone DocumentSet" in new BaseScope {
      val documentSet = CloneImportJobStore.insertCloneOf(DocumentSet(createdAt=new Timestamp(1L)))
      documentSet.createdAt.getTime must beCloseTo(scala.compat.Platform.currentTime, 1000)
    }

    "insert a cloned UploadedFile for a clone DocumentSet" in new BaseScope {
      val uploadedFile = UploadedFileStore.insertOrUpdate(UploadedFile(contentDisposition = "content-disposition", contentType = "content-type", size = 100))
      val documentSet = DocumentSetStore.insertOrUpdate(DocumentSet(title="title", uploadedFileId = Some(uploadedFile.id)))

      val cloneDocumentSet = CloneImportJobStore.insertCloneOf(documentSet)

      cloneDocumentSet.uploadedFileId must not beSome(uploadedFile.id)
      cloneDocumentSet.uploadedFileId must not beNone
      // The following newline prevents a compiler error...

      UploadedFileFinder.byDocumentSet(cloneDocumentSet).count must beEqualTo(1)
    }
  }

  step(stop)
}
