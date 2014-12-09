package models.archive

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class FileViewInfoSpec extends Specification {

  "FileViewInfo" should {
    "create ArchiveEntry" in new FileViewInfoContext {
      override val originalName = "title.doc"
      entry.name must beEqualTo("title_doc.pdf")
    }

    "only have a single .pdf extension for pdf files" in new FileViewInfoContext {
      override val originalName = "title.pdf"
      entry.name must beEqualTo("title.pdf")
    }

    "detect PDF extension regardless of case" in new FileViewInfoContext {
      override val originalName = "title.PDF"
      entry.name must beEqualTo("title.pdf")
    }

    "pass size along" in new FileViewInfoContext {
      override val size = 1234L
      entry.size must beEqualTo(1234L)
    }
  }
  
  trait FileViewInfoContext extends Scope {
    val viewOid = 123L
    val originalName = "title"
    val size = 234L

    def viewInfo = new TestFileViewInfo(originalName, viewOid, size)
    def entry = viewInfo.archiveEntry
  }

  class TestFileViewInfo(title: String, oid: Long, size: Long)
    extends FileViewInfo(title, oid, size) {

    override def stream = ???
  }
}
