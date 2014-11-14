package models.archive
import org.specs2.matcher.MatchResult

class FileViewInfoSpec extends ViewInfoSpecification {

  "FileViewInfo" should {

    "create ArchiveEntry" in new FileViewInfoContext {
      entry must matchParameters(cleanName + Pdf, size, viewOid)
    }

    "only have a single .pdf extension for pdf files" in new PdfFileContext {
      entry must matchParameters(originalName, size, viewOid)
    }

    "detect PDF extension regardless of case" in new UpperCasePdfFileContext {
      entry must matchParameters(baseName + Pdf, size, viewOid)
    }
  }
  
  trait FileViewInfoContext extends ArchiveEntryFactoryContext {
    val viewOid = 123l

    val fileViewInfo = FileViewInfo(originalName, viewOid, size)

    val viewInfo = new TestFileViewInfo(originalName, viewOid, size)
    val entry = viewInfo.archiveEntry

    override def streamWasCreatedFromId(id: Long): MatchResult[Any] =
      there was one(viewInfo.mockStorage).largeObjectInputStream(id)
  }

  trait PdfFileContext extends FileViewInfoContext {
    override def originalName = "file.pdf"
  }

  trait UpperCasePdfFileContext extends FileViewInfoContext {
    def baseName = "file"
    override def originalName = baseName + Pdf.toUpperCase
  }

  class TestFileViewInfo(title: String, oid: Long, size: Long) extends FileViewInfo(title, oid, size) {
    override val storage = smartMock[Storage]
    val mockStorage = storage
  }

}