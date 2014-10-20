package controllers

import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import models.archive.Archive
import models.DocumentFileInfo
import models.archive.ArchiveEntryFactory
import models.archive.ArchiveEntry

class DocumentSetArchiveControllerSpec extends ControllerSpecification with Mockito {

  "DocumentSetArchiveController" should {

    "set content-type" in new DocumentSetArchiveContext {
      header(h.CONTENT_TYPE) must beSome(contentType)
    }

    "set content-length to archive size" in new DocumentSetArchiveContext {
      header(h.CONTENT_LENGTH) must beSome(s"$archiveSize")
    }

    "set content-disposition to some cool name" in new DocumentSetArchiveContext {
      todo
    }
  }

  trait DocumentSetArchiveContext extends Scope {
    val documentSetId = 23
    val request = fakeAuthorizedRequest
    val archiveSize = 1989

    val documentFileInfos = Seq.fill(5)(smartMock[DocumentFileInfo])

    val contentType = "application/octet-stream"
    val controller = new DocumentSetArchiveController {
      val archiver = smartMock[Archiver]
      val archive = smartMock[Archive]

      archive.size returns archiveSize

      val storage = smartMock[Storage]
      storage.findDocumentFileInfo(documentSetId) returns documentFileInfos

      val archiveEntries = Seq.fill(5)(smartMock[ArchiveEntry])
      
      val archiveEntryFactory = smartMock[ArchiveEntryFactory]
      archiveEntryFactory.create(any) returns (archiveEntries.headOption, archiveEntries.tail.map(Some(_)): _*)

      archiver.createArchive(archiveEntries) returns archive      
    }

    lazy val result = controller.archive(documentSetId)(request)

    def header(key: String): Option[String] = h.header(key, result)
  }
}