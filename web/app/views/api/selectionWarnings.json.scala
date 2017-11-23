package views.json.api

import play.api.libs.json.{JsArray,JsNumber,JsObject,JsString}

import com.overviewdocs.query.{Field,FieldInSearchIndex}
import com.overviewdocs.searchindex.SearchWarning
import controllers.backend.ViewFilterBackend
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
        case SearchWarning.IndexDoesNotExist => JsObject(Seq(
          "type" -> JsString("IndexDoesNotExist")
        ))
      }
      case SelectionWarning.ViewFilterError(vfe) => vfe match {
        case ViewFilterBackend.ResolveError.UrlNotFound => JsObject(Seq(
          "type" -> JsString("ViewFilterUrlMissing")
        ))
        case ViewFilterBackend.ResolveError.HttpTimeout(url) => JsObject(Seq(
          "type" -> JsString("ViewFilterHttpTimeout"),
          "url" -> JsString(url)
        ))
        case ViewFilterBackend.ResolveError.PluginError(url, message) => JsObject(Seq(
          "type" -> JsString("ViewFilterPluginError"),
          "url" -> JsString(url),
          "message" -> JsString(message)
        ))
      }
      case SelectionWarning.RegexSyntaxError(regex, errorEnglish, index) => JsObject(Seq(
        "type" -> JsString("RegexSyntaxError"),
        "regex" -> JsString(regex),
        "message" -> JsString(errorEnglish),
        "index" -> JsNumber(index)
      ))
      case SelectionWarning.NestedRegexIgnored(regex) => JsObject(Seq(
        "type" -> JsString("NestedRegexIgnored"),
        "regex" -> JsString(regex)
      ))
      case SelectionWarning.RegexLimited(nTotal, nTested) => JsObject(Seq(
        "type" -> JsString("RegexLimited"),
        "nTotal" -> JsNumber(nTotal),
        "nTested" -> JsNumber(nTested)
      ))
      case SelectionWarning.MissingField(fieldName, validFieldNames) => JsObject(Seq(
        "type" -> JsString("MissingField"),
        "field" -> JsString(fieldName),
        "validFieldNames" -> JsArray(validFieldNames.map(JsString.apply _))
      ))
    }
  }
}
