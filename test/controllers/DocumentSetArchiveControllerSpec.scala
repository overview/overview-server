package controllers

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

import controllers.backend.DocumentFileInfoBackend
import models.archive.{Archive,ArchiveEntry,DocumentViewInfo}

class DocumentSetArchiveControllerSpec extends ControllerSpecification with Mockito {
  "DocumentSetArchiveController" should {

    "set content-type" in new BaseScope {
      header(h.CONTENT_TYPE) must beSome(contentType)
    }

    "set content-length to archive size" in new BaseScope {
      header(h.CONTENT_LENGTH) must beSome(s"$archiveSize")
    }

    "set content-disposition to some cool name" in new BaseScope {
      header(h.CONTENT_DISPOSITION) must beSome(s"""attachment; filename="$fileName"""")
    }

    "send archive as content" in new BaseScope {
      h.contentAsBytes(result) must be equalTo archiveData
    }

    "redirect if archiving is not supported" in new BaseScope {
      override def numberOfDocuments = 0
      h.status(result) must beEqualTo(h.SEE_OTHER)
      h.flash(result).data must contain("warning" -> "controllers.DocumentSetArchiveController.unsupported")
    }

    "flash warning if there are too many documents" in new BaseScope {
      override def numberOfDocuments = 11
      h.flash(result).data must contain("warning" -> "controllers.DocumentSetArchiveController.tooManyEntries")
    }

    "flash warning if archive is too large" in new BaseScope {
      override def controller = new TooLargeArchiveController(viewInfos)
      h.flash(result).data must contain("warning" -> "controllers.DocumentSetArchiveController.archiveTooLarge")
    }
  }

  trait BaseScope extends Scope {
    import scala.concurrent.ExecutionContext.Implicits.global

    val documentSetId = 23
    val fileName = "documents.zip"
    val request = fakeAuthorizedRequest
    val archiveSize = 1989
    val archiveData = Array.fill(archiveSize)(0xda.toByte)

    val contentType = "application/x-zip-compressed"

    def numberOfDocuments = 5
    def viewInfos: Seq[DocumentViewInfo] = Seq.fill(numberOfDocuments) {
      val entry = smartMock[ArchiveEntry]
      val viewInfo = smartMock[DocumentViewInfo]
      viewInfo.archiveEntry returns entry
      viewInfo
    }

    def controller: DocumentSetArchiveController =
      new TestDocumentSetArchiveController(documentSetId, archiveData, viewInfos)

    def result = controller.archive(documentSetId, fileName)(request)

    def header(key: String): Option[String] = h.header(key, result)
  }

  class TestDocumentSetArchiveController(
      documentSetId: Long,
      archiveData: Array[Byte],
      documentViewInfos: Seq[DocumentViewInfo]
  ) extends DocumentSetArchiveController with TestController {
    import scala.concurrent.ExecutionContext.Implicits.global

    override val MaxNumberOfEntries = 10

    val archiver = smartMock[Archiver]
    val archive = smartMock[Archive]

    archive.size returns archiveData.length
    archive.stream returns Enumerator(archiveData)

    val backend = smartMock[DocumentFileInfoBackend]
    backend.indexDocumentViewInfos(any) returns Future.successful(documentViewInfos)

    archiver.createArchive(any) returns archive
  }

  class TooLargeArchiveController(documentViewInfos: Seq[DocumentViewInfo])
  extends DocumentSetArchiveController with TestController {
    import scala.concurrent.ExecutionContext.Implicits.global

    val archiver = smartMock[Archiver]
    val archive = smartMock[Archive]

    archive.size returns MaxArchiveSize + 1
    archiver.createArchive(any) returns archive

    val backend = smartMock[DocumentFileInfoBackend]
    backend.indexDocumentViewInfos(any) returns Future.successful(documentViewInfos)
  }
}
