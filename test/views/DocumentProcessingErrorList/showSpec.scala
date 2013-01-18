package views.json.DocumentProcessingErrorList

import org.overviewproject.test.Specification
import org.overviewproject.tree.orm.DocumentProcessingError

class showSpec extends Specification {

  "Json for DocumentProcessingError list" should {
    
    "contain errors with urls" in {
      val documentSetId = 1
      val numErrors = 5
      val errors = Seq.tabulate(numErrors)(i => DocumentProcessingError(documentSetId, "url-" + i, "message"))
      
      val errorJson = show(errors).toString
      
      errorJson must beMatching(".*\"document-processing-errors\":\\[(.*url-.*,?){5}\\].*".r)
    }
  }
  
}