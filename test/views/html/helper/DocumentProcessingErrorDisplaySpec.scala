package views.html.helper

import org.overviewproject.test.Specification

class DocumentProcessingErrorDisplaySpec extends Specification {
  
  val prefix: String = "views.DocumentProcessingError.error.td."
  val internalError: String = prefix + "internal_error"
  val accessDenied: String = prefix + "access_denied"
  val notFound: String = prefix + "not_found"
  val serverError: String = prefix + "server_error"
  
  "ErrorStatusMessage" should {
    
    "show internal error if no status code is given" in {
      DocumentProcessingErrorDisplay.status(None) must be equalTo internalError
    }
    
    "show access denied error on 403" in {
      DocumentProcessingErrorDisplay.status(Some(403)) must be equalTo accessDenied
    }
    
    "show not found error on 404" in {
      DocumentProcessingErrorDisplay.status(Some(404)) must be equalTo notFound
    }
    
    "show server error for other errors" in {
      DocumentProcessingErrorDisplay.status(Some(406)) must be equalTo serverError
    }
    
    "show complete URL if less than 40 characters" in {
      val url = "short url"
      DocumentProcessingErrorDisplay.url(url) must be equalTo url
    }
    
   "show last 40 chars of a url, preceeded by ellipsis" in {
     val urlShown = "*" * 40
     val url = "not shown" + urlShown 
     
     DocumentProcessingErrorDisplay.url(url) must be equalTo "\u2026" + urlShown
   } 
   
  }

}