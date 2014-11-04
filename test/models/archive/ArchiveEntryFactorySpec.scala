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
import models.FileViewInfo
import models.PageViewInfo

class ArchiveEntryFactorySpec extends Specification with Mockito {

  "ArchiveEntryFactory" should {

    "create entries for FileViews" in new FileViewInfoContext {
      entries.headOption must beSome(matchesEntryParams(name + Pdf, size, viewOid) _)
    }

    "create entries for PageViews" in new PageViewInfoContext {
      entries.headOption must beSome(matchesEntryParams(pageTitle + Pdf, size, pageId) _)
    }

    "only have a single .pdf extension for pdf files" in new PdfFileContext {
      entries.headOption must beSome(matchesEntryParams(name, size, viewOid) _)
    }

    "detect PDF extension regardless of case" in new UpperCasePdfFileContext {
      entries.headOption must beSome(matchesEntryParams(name, size, viewOid) _)
    }

    "remove pdf extension from filename with page" in new PdfPageContext {
      entries.headOption must beSome(matchesEntryParams(s"$baseName $pageDescriptor" + Pdf, size, pageId) _)
    }
  }

  
  trait ArchiveEntryFactoryContext extends Scope {
    val Pdf = ".pdf"
    def name = "file.doc"
    
    val size = 3418913

    def matchesEntryParams(name: String, size: Long, oid: Long)(e: ArchiveEntry) = {
      e.name must be equalTo name
      e.size must be equalTo size

      val s = e.data()
      streamWasCreatedFromId(oid)
    }

    def streamWasCreatedFromId(id: Long): MatchResult[Any] 
    
  }
  
  trait FileViewInfoContext extends ArchiveEntryFactoryContext {
    val viewOid = 123l

    val fileViewInfo = FileViewInfo(name, viewOid, size)

    val factory = new TestArchiveEntryFactory(viewOid)
    val entries = factory.createFromFileViewInfos(Seq(fileViewInfo))

    override def streamWasCreatedFromId(id: Long): MatchResult[Any] =
      there was one(factory.mockStorage).largeObjectInputStream(id)
  }

  
  trait PageViewInfoContext extends ArchiveEntryFactoryContext {
    val pageNumber = 5
    val pageDescriptor = s"- p. $pageNumber"
    val pageTitle = s"$name $pageDescriptor"

    val pageId = 1l

    val pageViewInfo = PageViewInfo(name, pageNumber, pageId, size)

    val factory = new TestArchiveEntryFactory(pageId)
    val entries = factory.createFromPageViewInfos(Seq(pageViewInfo))

    override def streamWasCreatedFromId(id: Long) =
      there was one(factory.mockStorage).pageDataStream(pageId)

  }

  trait PdfFileContext extends FileViewInfoContext {
    override def name = "file.pdf"
  }

  trait UpperCasePdfFileContext extends FileViewInfoContext {
    override def name = "file.PDF"
  }

  trait PdfPageContext extends PageViewInfoContext {
    def baseName = "file"
    override def name = baseName + Pdf
  }

  class TestArchiveEntryFactory(id: Long) extends ArchiveEntryFactory {

    override protected val storage = smartMock[Storage]
    storage.largeObjectInputStream(id) returns smartMock[InputStream]

    storage.pageDataStream(id) returns Some(smartMock[InputStream])
    def mockStorage = storage
  }

}