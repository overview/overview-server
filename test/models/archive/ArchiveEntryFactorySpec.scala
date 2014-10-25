package models.archive

import org.specs2.mutable.Specification
import models.DocumentFileInfo
import org.specs2.mock.Mockito
import controllers.util.PlayLargeObjectInputStream
import org.overviewproject.models.File
import org.overviewproject.models.Page
import java.io.InputStream
import org.specs2.specification.Scope
import org.specs2.mutable.Before
import org.specs2.matcher.MatchResult

class ArchiveEntryFactorySpec extends Specification with Mockito {

  "ArchiveEntryFactory" should {

    "create entry for document with file" in new ArchiveEntryFactoryContext {
      entry must beSome(matchesEntryParams(name + Pdf, size, viewOid) _)
    }

    "create entry for document with PDF view" in new FileWithView {
      entry must beSome(matchesEntryParams(name + Pdf, viewSize, viewOid) _)
    }

    "create entry for document with page" in new DocumentWithPage {
      entry must beSome(matchesEntryParams(pageTitle + Pdf, pageSize, pageId) _)
    }

    "throw exception if pageId is invalid and stream is accessed" in new InvalidPageId {
      entry must beSome { e: ArchiveEntry =>
        e.data() must throwA[NoSuchElementException]
      }
    }

    "only have a single .pdf extension for pdf files" in new PdfFile {
      entry must beSome(matchesEntryParams(name, size, viewOid) _)
    }

    "detect PDF extension regardless of case" in new UpperCasePdfFile {
      entry must beSome(matchesEntryParams(name, size, viewOid) _)
    }

    "remove pdf extension from filename with page" in new PdfPage {
      entry must beSome(matchesEntryParams(s"$baseName $pageDescriptor" + Pdf, pageSize, pageId) _)
    }
  }

  trait ArchiveEntryFactoryContext extends Before {
    val Pdf = ".pdf"
    val contentsOid = 11l
    val viewOid = 11l

    def documentInfo = DocumentFileInfo(Some(name), Some(1l), None, None)
    val size = 100l
    val viewSize = 100l

    val name = "filename"
    val file = smartMock[File]

    val factory = new TestArchiveEntryFactory(contentsOid, file, None)
    def entry = factory.create(documentInfo)

    def before = {
      file.name returns name
      file.contentsSize returns Some(size)
      file.viewSize returns Some(viewSize)
      file.contentsOid returns contentsOid
      file.viewOid returns viewOid
    }

    def matchesEntryParams(name: String, size: Long, oid: Long)(e: ArchiveEntry) = {
      e.name must be equalTo name
      e.size must be equalTo size

      val s = e.data()
      streamWasCreatedFromId(oid)
    }

    def streamWasCreatedFromId(id: Long): MatchResult[Any] =
      there was one(factory.mockStorage).largeObjectInputStream(id)
  }

  trait FileWithView extends ArchiveEntryFactoryContext {
    override val contentsOid = 10l
    override val viewSize = 324l
  }

  trait DocumentWithPage extends ArchiveEntryFactoryContext {
    val pageId = 2l
    val pageNumber = 33
    val pageDescriptor = s"- page $pageNumber"
    val pageTitle = s"$name $pageDescriptor"

    val pageSize = 123

    override def documentInfo = DocumentFileInfo(Some(name), Some(1l), Some(pageId), Some(pageNumber))

    override val factory = new TestArchiveEntryFactory(1l, file, Some(pageSize))

    override def streamWasCreatedFromId(id: Long) =
      there was one(factory.mockStorage).pageDataStream(pageId)
  }

  trait InvalidPageId extends DocumentWithPage {
    override val factory = new TestArchiveEntryFactory(1l, file, Some(pageSize), validPageId = false)
  }

  trait PdfFile extends ArchiveEntryFactoryContext {
    override val name = "filename.pdf"
  }

  trait UpperCasePdfFile extends ArchiveEntryFactoryContext {
    override val name = "filename.PDF"
  }

  trait PdfPage extends DocumentWithPage {
    val baseName = "fileName"
    override val name = baseName + Pdf
  }

  class TestArchiveEntryFactory(oid: Long, file: File, pageSize: Option[Long], validPageId: Boolean = true) extends ArchiveEntryFactory {

    override protected val storage = smartMock[Storage]
    storage.findFile(any) returns Some(file)
    storage.largeObjectInputStream(oid) returns smartMock[InputStream]

    storage.findPageSize(any) returns pageSize

    if (validPageId) storage.pageDataStream(any) returns Some(smartMock[InputStream])
    else storage.pageDataStream(any) returns None

    def mockStorage = storage

  }
}