package models.archive
import org.specs2.matcher.MatchResult

class FileViewInfoSpec extends ViewInfoSpecification {

  "FileViewInfo" should {

    "create ArchiveEntry" in new FileViewInfoContext {
      matchesEntryParams(cleanName + Pdf, size, viewOid)(entry)
    }
  }

  "only have a single .pdf extension for pdf files" in new PdfFileContext {
    matchesEntryParams(originalName, size, viewOid)(entry)
  }

  "detect PDF extension regardless of case" in new UpperCasePdfFileContext {
    matchesEntryParams(baseName + Pdf, size, viewOid)(entry)
  }

  trait FileViewInfoContext extends ArchiveEntryFactoryContext {
    val viewOid = 123l

    val fileViewInfo = FileViewInfo(originalName, viewOid, size)

    val factory = new TestArchiveEntryFactory(viewOid)
    val entries = factory.createFromFileViewInfos(Seq(fileViewInfo))
    
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

  class TestFileViewInfo(title: String, oid: Long, size: Long) extends FileViewInfo1(title, oid, size) {
    override val storage = smartMock[Storage]
    val mockStorage = storage
  }

}