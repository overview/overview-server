package org.overviewproject.jobhandler.filegroup

import java.io.{ByteArrayInputStream,InputStream}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class PdfBoxPdfProcessorSpec extends Specification {
  "PdfBoxPdfProcessor" should {
    "invoke textify" in {
      val processor = new PdfBoxPdfProcessor {
        override protected def inputStreamToRawText(inputStream: InputStream) : String = "mockString"
        override protected def textify(rawText: String) : String = "textified"
      }

      processor.extractText(new ByteArrayInputStream(Array[Byte]())) must beEqualTo("textified")
    }
  }
}
