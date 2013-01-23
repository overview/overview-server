package views.html.helper

import org.overviewproject.test.Specification

class DocumentProcessingErrorDisplaySpec extends Specification {
  
  
  "ErrorStatusMessage" should {

    "show complete URL if less than 65 characters" in {
      val url = "short url"
      DocumentProcessingErrorDisplay.url(url) must be equalTo url
    }
    
   "show last 65 chars of a url, preceeded by ellipsis" in {
     val urlShown = "*" * 65
     val url = "not shown" + urlShown 
     
     DocumentProcessingErrorDisplay.url(url) must be equalTo "\u2026" + urlShown
   } 
   
  }

}