package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import java.io.ByteArrayInputStream
import org.overviewproject.tree.orm.File
import java.io.InputStream
import org.specs2.specification.Scope
import org.overviewproject.tree.orm.GroupedFileUpload

class CreateFileSpec extends Specification with Mockito {

  "CreatePdfView" should {

    "return original oid if original is a pdf" in new PdfFileContext {
      val file = createFile(upload)

      there was one(createFile.storage).createFile(name, oid)
    }

    "convert rewound stream to pdf if original is not pdf" in new NoPdfFileContext {
      val file = createFile(upload)

      there was one(createFile.converter).createFileWithPdfView(upload, inputStream)
      inputStream.available must be equalTo(fileText.length)
    }

    trait CreateFileContext extends Scope {
      val fileText: String
      val createdFile: File 
      
      val name = "filename"
      val oid = 1l
      val upload = smartMock[GroupedFileUpload]
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

    override val storage = mock[Storage]

    storage.getLargeObjectInputStream(any) returns inputStream
    storage.createFile(any, any) returns createdFile
    
    override val converter = mock[DocumentConverter]
    converter.createFileWithPdfView(any, any) returns createdFile
  }

}