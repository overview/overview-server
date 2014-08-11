package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import java.io.ByteArrayInputStream
import org.overviewproject.tree.orm.File
import java.io.InputStream
import org.specs2.specification.Scope
import org.overviewproject.tree.orm.GroupedFileUpload
import java.util.UUID

class CreateFileSpec extends Specification with Mockito {
  "CreatePdfView" should {
    "return original oid if original is a pdf" in new PdfFileContext {
      val file = createFile(documentSetId, upload)

      there was one(createFile.storage).createFile(documentSetId, name, oid)
    }

    "convert rewound stream to pdf if original is not pdf" in new NoPdfFileContext {
      val file = createFile(documentSetId, upload)

      there was one(createFile.storage).createFileWithPdfView(any, any, any)
      inputStream.available must be equalTo(fileText.length)
    }

    trait CreateFileContext extends Scope {
      val fileText: String
      val createdFile: File

      val documentSetId = 1l
      val name = "filename"
      val oid = 1l
      val upload = smartMock[GroupedFileUpload]
      val guid: UUID = UUID.randomUUID()
      upload.name returns name
      upload.contentsOid returns oid

      lazy val inputStream = new ByteArrayInputStream(fileText.getBytes)
      lazy val createFile: TestCreateFile = new TestCreateFile(inputStream, createdFile)
    }

    trait PdfFileContext extends CreateFileContext {
      override val fileText: String = "%PDF and stuff"
      override val createdFile: File = File(1, oid, oid, name)
    }

    trait NoPdfFileContext extends CreateFileContext {
      val viewOid = 2l
      override val createdFile: File = File(1, oid, viewOid, name)

      override val fileText: String = "Not a PDF"
    }
  }

  class TestCreateFile(inputStream: InputStream, createdFile: File) extends CreateFile {

    override val storage = mock[CreateFile.Storage]
    private val convertedStream = mock[InputStream]

    storage.getLargeObjectInputStream(any) returns inputStream
    storage.createFile(any, any, any) returns createdFile

    override val converter = new TestConverter

    class TestConverter extends DocumentConverter {
      override def withStreamAsPdf[T](guid: UUID, filename: String, documentStream: InputStream)(f: InputStream => T): T = {
        f(convertedStream)
      }
    }
  }
}
