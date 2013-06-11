package views.json.helper

import play.api.i18n.Lang
import play.api.libs.json.{ JsValue, Writes }
import play.api.libs.json.Json.toJson

import models.core.DocumentIdList

object ModelJsonConverters {

  implicit object JsonDocumentIdList extends Writes[DocumentIdList] {
    override def writes(documentIdList: DocumentIdList): JsValue = {
      toJson(Map(
        "docids" -> toJson(documentIdList.firstIds),
        "n" -> toJson(documentIdList.totalCount)))
    }
  }
}
