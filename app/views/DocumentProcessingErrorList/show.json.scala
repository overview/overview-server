package views.json.DocumentProcessingErrorList

import org.overviewproject.tree.orm.DocumentProcessingError
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

object show {

  def apply(documentProcessingErrorList: Seq[DocumentProcessingError]): JsValue = {
    toJson(
      Map(
          "document-processing-errors" -> documentProcessingErrorList.map(_.textUrl)
          )    
    )
    
  }
}