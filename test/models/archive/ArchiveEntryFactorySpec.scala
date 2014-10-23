package models.archive

import org.specs2.mutable.Specification
import models.DocumentFileInfo
import org.specs2.mock.Mockito
import controllers.util.PlayLargeObjectInputStream
import org.overviewproject.models.File
import java.io.InputStream

class ArchiveEntryFactorySpec extends Specification with Mockito {

  "ArchiveEntryFactory" should {

    "create entry for document with file" in {
      val contentsOid = 11l
      val viewOid = 11l

      val documentInfo = DocumentFileInfo(Some("title"), Some(1l), None, None)
      val size = 100l
      val name = "filename"
      val file = smartMock[File]
      file.name returns name
      file.contentsSize returns Some(size)
      file.viewSize returns Some(size)
      file.contentsOid returns contentsOid
      file.viewOid returns viewOid
      
      val factory = new TestArchiveEntryFactory(contentsOid, file)

      val entry = factory.create(documentInfo)

      entry must beSome { e: ArchiveEntry =>
        e.name must be equalTo name
        e.size must be equalTo size
        
        val s = e.data()
        there was one(factory.mockStorage).largeObjectInputStream(viewOid)
      }

      
    }

    "create entry for document with PDF view" in {
      val contentsOid = 11l
      val viewOid = 22l

      val documentInfo = DocumentFileInfo(Some("title"), Some(1l), None, None)
      val size = 100l
      val viewSize = 324l
      val name = "filename"
      val file = smartMock[File]

      file.name returns name
      file.contentsSize returns Some(size)
      file.viewSize returns Some(viewSize)
      file.viewOid returns viewOid
      
      val factory = new TestArchiveEntryFactory(viewOid, file)

      val entry = factory.create(documentInfo)

      entry must beSome { e: ArchiveEntry =>
        e.name must be equalTo name
        e.size must be equalTo viewSize
        val s = e.data()
        there was one(factory.mockStorage).largeObjectInputStream(viewOid)
      }

    }

    "create entry for document with page" in {
      todo
    }
  }

  class TestArchiveEntryFactory(oid: Long, file: File) extends ArchiveEntryFactory {

    override protected val storage = smartMock[Storage]
    storage.findFile(any) returns Some(file)
    storage.largeObjectInputStream(oid) returns smartMock[InputStream]

    def mockStorage = storage
  }
}