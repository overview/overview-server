package views.json.api

import play.api.libs.json.{JsArray,JsNumber,JsObject,JsString}

import com.overviewdocs.query.{Field,FieldInSearchIndex}
import com.overviewdocs.searchindex.SearchWarning
import models.SelectionWarning

object selectionWarnings {
  def apply(warnings: List[SelectionWarning]): JsArray = {
    JsArray(warnings.map(applyOne _))
  }

  private def fieldName(field: FieldInSearchIndex): String = field match {
    case Field.Notes => "notes"
    case Field.Text => "text"
    case Field.Title => "title"
    case Field.Metadata(name) => name
  }

  private def applyOne(warning: SelectionWarning): JsObject = {
    warning match {
      case SelectionWarning.SearchIndexWarning(siw) => siw match {
        case SearchWarning.TooManyExpansions(field, term, nExpansions) => JsObject(Seq(
          "type" -> JsString("TooManyExpansions"),
          "field" -> JsString(fieldName(field)),
          "term" -> JsString(term),
          "nExpansions" -> JsNumber(nExpansions)
        ))
        case SearchWarning.TooMuchFuzz(field, term, allowedFuzz) => JsObject(Seq(
          "type" -> JsString("TooMuchFuzz"),
          "field" -> JsString(fieldName(field)),
          "term" -> JsString(term),
          "allowedFuzz" -> JsNumber(allowedFuzz)
        ))
      }
    }
  }
}
