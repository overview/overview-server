package models.archive

import org.specs2.mutable.Specification
import models.DocumentFileInfo
import org.specs2.mock.Mockito
import controllers.util.PlayLargeObjectInputStream
import org.overviewproject.models.File

class ArchiveEntryFactorySpec extends Specification with Mockito {

  "ArchiveEntryFactory" should {

    "create entry for document with file" in {
      val documentInfo = DocumentFileInfo(Some("title"), Some(1l), None, None)
      val size = 100l
      val name = "filename"
      val file = smartMock[File]
      file.name returns name
      file.contentsSize returns Some(size)
      
      val factory = new TestArchiveEntryFactory(file)

      val entry = factory.create(documentInfo)

      entry must beSome { e: ArchiveEntry =>
        e.name must be equalTo name
        e.size must be equalTo size
        e.data() must haveClass[PlayLargeObjectInputStream]
      }
    }

    "create entry for document with PDF view" in {
      todo
    }

    "create entry for document with page" in {
      todo
    }
  }

  class TestArchiveEntryFactory(file: File) extends ArchiveEntryFactory {
    
    override protected val storage = smartMock[Storage]
    storage.findFile(any) returns Some(file)
  }
}