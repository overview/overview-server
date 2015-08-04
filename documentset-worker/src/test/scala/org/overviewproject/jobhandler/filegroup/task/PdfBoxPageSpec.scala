package com.overviewdocs.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.OutputStream
import java.awt.image.BufferedImage
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDDocumentCatalog
import scala.collection.JavaConverters._

class PdfBoxPageSpec extends Specification with Mockito {

  "PdfBoxPage" should {

    "return data" in new PdfContext {
      testPage.data must be equalTo(pageData)
    }
    
    "return text" in new PdfContext {
      testPage.text must be equalTo(pageText)
    }
    
    "render page as an image" in new PdfContext {
      testPage.image must be equalTo(pageImage)
    }
    
    "get text with font" in new PdfContext {
      testPage.textWithFonts must beRight(pageText)
    }
  }

  trait PdfContext extends Scope {
    val document = smartMock[PDDocument]
    val textStripper = smartMock[PdfBoxPage.FontDetectingTextStripper]
    val pageData = Array[Byte](1, 2, 3, 4)
    val pageText = "page text"
    val pageImage = smartMock[BufferedImage]
    val catalog = smartMock[PDDocumentCatalog]
    val page = smartMock[PDPage]
    val pageList = List(page).asJava
    
    document.save(any[OutputStream]) answers { _.asInstanceOf[OutputStream].write(pageData) }
    document.getDocumentCatalog returns catalog
    catalog.getAllPages.asInstanceOf[java.util.List[PDPage]] returns pageList

    page.convertToImage(BufferedImage.TYPE_BYTE_GRAY, 400) returns pageImage
    
    textStripper.getText(document) returns pageText
    textStripper.foundFonts returns true
    
    val testPage = new TestPdfBoxPage(document, textStripper)
  }

  class TestPdfBoxPage(
    override protected val document: PDDocument,
    override protected val textStripper: PdfBoxPage.FontDetectingTextStripper) extends PdfBoxPage

}