package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import java.io.ByteArrayInputStream
import org.overviewproject.tree.orm.File
import java.io.InputStream
import org.specs2.specification.Scope

class CreateFileSpec extends Specification with Mockito {

  "CreatePdfView" should {

    "return original oid if original is a pdf" in new PdfFileContext {
      val file = createFile(name, oid)

      file.viewOid must be equalTo (oid)
      file.contentsOid must be equalTo (oid)
    }

    "convert rewound stream to pdf if original is not pdf" in new NoPdfFileContext {
      val file = createFile(name, oid)

      file.viewOid must be equalTo (viewOid)
      file.contentsOid must be equalTo (oid)

      inputStream.available must be equalTo(fileText.length)
    }

    trait CreateFileContext extends Scope {
      val fileText: String

      val name = "filename"
      val oid = 1l

      lazy val inputStream = new ByteArrayInputStream(fileText.getBytes)
      lazy val createFile: CreateFile = new TestCreateFile(inputStream)
    }

    trait PdfFileContext extends CreateFileContext {
      override val fileText: String = "%PDF and stuff"
    }
    
    trait NoPdfFileContext extends CreateFileContext {
      val viewOid = 2l
      override val fileText: String = "Not a PDF"
    }
  }

  class TestCreateFile(inputStream: InputStream) extends CreateFile {

    override val storage = mock[Storage]

    storage.getLargeObjectInputStream(any) returns inputStream

    override val converter = mock[DocumentConverter]
    converter.createFileWithPdfView(any, any, any) returns File(1, 1l, 2l, "filename")
  }

}