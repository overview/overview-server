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

class ArchiveEntryFactorySpec extends Specification with Mockito {

  "ArchiveEntryFactory" should {

    "create entry for document with file" in new ArchiveEntryFactoryContext {
      entry must beSome(matchesEntryParams(name, size, viewOid) _)
    }

    "create entry for document with PDF view" in new FileWithView {
      entry must beSome(matchesEntryParams(name, viewSize, viewOid) _)
    }

    "create entry for document with page" in new DocumentWithPage {

      entry must beSome { e: ArchiveEntry =>
        e.name must be equalTo s"$name - page $pageNumber"
      }
    }
    
    "create entry for document with missing page" in  {
      todo
    }
  }

  trait ArchiveEntryFactoryContext extends Before {
    val contentsOid = 11l
    val viewOid = 11l

    val documentInfo = DocumentFileInfo(Some(name), Some(1l), None, None)
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
      e.size must be equalTo viewSize
      val s = e.data()
      there was one(factory.mockStorage).largeObjectInputStream(oid)
    }
  }

  trait FileWithView extends ArchiveEntryFactoryContext {
    override val contentsOid = 10l
    override val viewSize = 324l
  }

  trait DocumentWithPage extends ArchiveEntryFactoryContext {
    val pageId = 2l
    val pageNumber = 33

    val pageSize = 123

    override val documentInfo = DocumentFileInfo(Some(name), Some(1l), Some(pageId), Some(pageNumber))

    override val factory = new TestArchiveEntryFactory(1l, file, Some(pageSize))

  }

  class TestArchiveEntryFactory(oid: Long, file: File, pageSize: Option[Long]) extends ArchiveEntryFactory {

    override protected val storage = smartMock[Storage]
    storage.findFile(any) returns Some(file)
    storage.largeObjectInputStream(oid) returns smartMock[InputStream]

    storage.findPageSize(any) returns pageSize

    def mockStorage = storage
  }
}