package controllers

import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import models.archive.Archive
import models.DocumentFileInfo
import models.archive.ArchiveEntryFactory
import models.archive.ArchiveEntry
import java.io.ByteArrayInputStream
import scala.concurrent.Future

class DocumentSetArchiveControllerSpec extends ControllerSpecification with Mockito {

  "DocumentSetArchiveController" should {

    "set content-type" in new DocumentSetArchiveContext {
      header(h.CONTENT_TYPE) must beSome(contentType)
    }

    "set content-length to archive size" in new DocumentSetArchiveContext {
      header(h.CONTENT_LENGTH) must beSome(s"$archiveSize")
    }

    "set content-disposition to some cool name" in new DocumentSetArchiveContext {
      header(h.CONTENT_DISPOSITION) must beSome(s"""attachment; filename="$fileName"""")
    }
    
    "send archive as content" in new DocumentSetArchiveContext {
      h.contentAsBytes(result) must be equalTo archiveData
    }
  }

  trait DocumentSetArchiveContext extends Scope {
    import scala.concurrent.ExecutionContext.Implicits.global
    
    val documentSetId = 23
    val fileName = "documents.zip"
    val request = fakeAuthorizedRequest
    val archiveSize = 1989
    val archiveData = Array.fill(archiveSize)(0xda.toByte)
    
    val documentFileInfos = Seq.fill(5)(smartMock[DocumentFileInfo])

    val contentType = "application/octet-stream"
    val controller = new DocumentSetArchiveController {
      val archiver = smartMock[Archiver]
      val archive = smartMock[Archive]

      archive.size returns archiveSize
      archive.stream returns new ByteArrayInputStream(archiveData)
      
      val storage = smartMock[Storage]
      storage.findDocumentFileInfo(documentSetId) returns Future(documentFileInfos)

      val archiveEntries = Seq.fill(5)(smartMock[ArchiveEntry])
      
      val archiveEntryFactory = smartMock[ArchiveEntryFactory]
      archiveEntryFactory.create(any) returns (archiveEntries.headOption, archiveEntries.tail.map(Some(_)): _*)

      archiver.createArchive(archiveEntries) returns archive      
    }

    lazy val result = controller.archive(documentSetId, fileName)(request)

    def header(key: String): Option[String] = h.header(key, result)
  }
}