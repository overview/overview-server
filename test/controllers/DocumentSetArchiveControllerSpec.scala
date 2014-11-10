package controllers

import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import models.archive.Archive
import models.DocumentFileInfo
import models.archive.ArchiveEntryFactory
import models.archive.ArchiveEntry
import java.io.ByteArrayInputStream
import scala.concurrent.Future
import play.api.i18n.Messages
import controllers.backend.DocumentFileInfoBackend
import models.FileViewInfo
import models.archive.PageViewInfo

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

    "create archive from pages if document set is split" in new SplitDocumentSet {
      h.contentAsBytes(result) must be equalTo archiveData
    }
    
    "redirect if archiving is not supported" in new UnsupportedDocumentSet {
      h.status(result) must beEqualTo(h.SEE_OTHER)
    }

    "flash warning if archiving is not supported" in new UnsupportedDocumentSet {
      val unsupported = Messages("controllers.DocumentSetArchiveController.unsupported")

      h.flash(result).data must be equalTo (Map("warning" -> unsupported))
    }
  }

  trait DocumentSetArchiveContext extends Scope {
    import scala.concurrent.ExecutionContext.Implicits.global

    val documentSetId = 23
    val fileName = "documents.zip"
    val request = fakeAuthorizedRequest
    val archiveSize = 1989
    val archiveData = Array.fill(archiveSize)(0xda.toByte)

    val contentType = "application/x-zip-compressed"

    def pageViewInfos: Seq[PageViewInfo] = Seq.empty
    def fileViewInfos: Seq[FileViewInfo] = Seq.fill(5)(smartMock[FileViewInfo])
    def result = {
      val controller = new TestDocumentSetArchiveController(documentSetId, archiveData, pageViewInfos, fileViewInfos)
      controller.archive(documentSetId, fileName)(request)
    }

    def header(key: String): Option[String] = h.header(key, result)
  }


  trait SplitDocumentSet extends DocumentSetArchiveContext {
    override def pageViewInfos = Seq.fill(10)(smartMock[PageViewInfo])
    override def fileViewInfos = Seq.empty
  }
  
  trait UnsupportedDocumentSet extends DocumentSetArchiveContext {
    override def pageViewInfos = Seq.empty
    override def fileViewInfos = Seq.empty
  }

  class TestDocumentSetArchiveController(
      documentSetId: Long,
      archiveData: Array[Byte],
      pageViewInfos: Seq[PageViewInfo],
      fileViewInfos: Seq[FileViewInfo]) extends DocumentSetArchiveController {
    import scala.concurrent.ExecutionContext.Implicits.global

    val archiver = smartMock[Archiver]
    val archive = smartMock[Archive]
    
    archive.size returns archiveData.length
    archive.stream returns new ByteArrayInputStream(archiveData)

    val backend = smartMock[DocumentFileInfoBackend]
    backend.indexDocumentFileInfosForFiles(documentSetId) returns Future(fileViewInfos)
    backend.indexDocumentFileInfosForPages(documentSetId) returns Future(pageViewInfos)
    
    val archiveEntriesFromPages = Seq.fill(pageViewInfos.length)(smartMock[ArchiveEntry])
    val archiveEntriesFromFiles = Seq.fill(fileViewInfos.length)(smartMock[ArchiveEntry])
    
    val archiveEntryFactory = smartMock[ArchiveEntryFactory]
    archiveEntryFactory.createFromPageViewInfos(any) returns archiveEntriesFromPages
    archiveEntryFactory.createFromFileViewInfos(fileViewInfos) returns archiveEntriesFromFiles
    
    archiver.createArchive(archiveEntriesFromPages ++ archiveEntriesFromFiles) returns archive

  }
}
