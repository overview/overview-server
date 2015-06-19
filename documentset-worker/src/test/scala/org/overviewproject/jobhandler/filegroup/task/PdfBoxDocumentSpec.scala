package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import java.io.File
import org.specs2.mutable.After


class PdfBoxDocumentSpec extends Specification {

  "PdfBoxDocument" should {
    
    "get text from document with font" in new WithFont {
      document.textWithFonts must beRight("I have text.\n")
    }
    
    "detect that file with only image has no font" in new WithoutFont {
      document.textWithFonts must beLeft
    }
  }
  
  trait DocumentContext extends After {
    lazy val document: PdfBoxDocument = new PdfBoxDocument(file)
    def file: File
    
    override def after = document.close
  }
  
  trait WithFont extends DocumentContext {
    override def file = new File("src/test/resources/WithText.pdf")
  }
  
  trait WithoutFont extends DocumentContext {
   override def file = new File("src/test/resources/NoText.pdf")    
  }
}