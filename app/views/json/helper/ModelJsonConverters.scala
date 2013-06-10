package views.json.helper

import play.api.i18n.Lang
import play.api.libs.json.{ JsValue, Writes }
import play.api.libs.json.Json.toJson

import models.PersistentTagInfo
import models.core.DocumentIdList

object ModelJsonConverters {

  implicit object JsonDocumentIdList extends Writes[DocumentIdList] {
    override def writes(documentIdList: DocumentIdList): JsValue = {
      toJson(Map(
        "docids" -> toJson(documentIdList.firstIds),
        "n" -> toJson(documentIdList.totalCount)))
    }
  }

  implicit object JsonPersistentTagInfo extends Writes[PersistentTagInfo] {
    override def writes(tag: PersistentTagInfo): JsValue = {
      val tagValues = Map(
        "id" -> toJson(tag.id),
        "name" -> toJson(tag.name),
        "doclist" -> toJson(tag.documentIds))

      val tagColorValue = maybeMap("color", tag.color, '#' + _) 

      toJson(tagValues ++ tagColorValue)
    }
  }
  
  private def maybeMap(key: String, maybeValue: Option[String], toValue: String => String = identity): Map[String, JsValue] =
    maybeValue.map(v => Map(key -> toJson(toValue(v)))).getOrElse(Map.empty)
}
