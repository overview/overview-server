package controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.Result
import scala.concurrent.Future

import com.overviewdocs.test.factories.{PodoFactory=>factory}
import controllers.auth.AuthorizedRequest
import controllers.backend.ArchiveEntryBackend
import models.archive.{ArchiveFactory,ZipArchive}
import models.{ArchiveEntry,InMemorySelection,Selection}

class DocumentSetArchiveControllerSpec extends ControllerSpecification with Mockito {
  trait BaseScope extends Scope {
    val mockArchiveEntryBackend = smartMock[ArchiveEntryBackend]
    val mockArchiveFactory = smartMock[ArchiveFactory]
    val selection = InMemorySelection(Seq(2L)) // override for a different selection
    def buildSelection: Future[Either[Result,Selection]] = Future(Right(selection)) // override for edge cases

    val controller = new DocumentSetArchiveController {
      override protected val archiveEntryBackend = mockArchiveEntryBackend
      override protected val archiveFactory = mockArchiveFactory
      override def requestToSelection(documentSetId: Long, request: AuthorizedRequest[_]) = buildSelection
    }
  }

  "DocumentSetArchiveController" should {
    "stream an archive" in new BaseScope {
      val mockArchive = smartMock[ZipArchive]
      val entries = Seq(ArchiveEntry(2L, "foo".getBytes("ascii"), 123L))
      mockArchiveEntryBackend.showMany(1L, Seq(2L)) returns Future.successful(entries)
      mockArchiveFactory.createZip(1L, entries) returns Right(mockArchive)
      mockArchive.size returns 6L
      mockArchive.stream returns Source.single(ByteString("abcdef".getBytes("ascii")))
      val result = controller.archive(1L, "filename.zip")(fakeAuthorizedRequest)
      h.header("Content-Type", result) must beSome("application/zip")
      h.header("Content-Length", result) must beSome("6")
      h.header("Content-Disposition", result) must beSome("attachment; filename=\"filename.zip\"")
      new String(h.contentAsBytes(result).toArray, "ascii") must beEqualTo("abcdef")
    }

    "redirect with flash if unable to create an archive" in new BaseScope {
      val entries = Seq(ArchiveEntry(2L, "foo".getBytes("ascii"), 123L))
      mockArchiveEntryBackend.showMany(1L, Seq(2L)) returns Future.successful(entries)
      mockArchiveFactory.createZip(1L, entries) returns Left("nope")
      val result = controller.archive(1L, "filename.zip")(fakeAuthorizedRequest)
      h.status(result) must beEqualTo(h.SEE_OTHER)
      h.flash(result).data must contain("warning" -> "controllers.DocumentSetArchiveController.nope")
    }

    "fail fast with >65535 files" in new BaseScope {
      // Errors normally come from ArchiveFactory. But we can take a shortcut
      // to avoid an expensive query.
      override val selection = InMemorySelection(Seq.tabulate(65536)(_.toLong))
      val result = controller.archive(1L, "filename.zip")(fakeAuthorizedRequest)
      h.status(result) must beEqualTo(h.SEE_OTHER)
      h.flash(result).data must contain("warning" -> controller.messagesApi("controllers.DocumentSetArchiveController.tooManyEntries"))
      there was no(mockArchiveFactory).createZip(any, any)
    }
  }
}
