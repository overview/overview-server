package views.json.helper

import models.PersistentTagInfo
import models.core.{ Document, DocumentIdList }
import play.api.i18n.Lang
import play.api.libs.json.{ JsValue, Writes }
import play.api.libs.json.Json.toJson
import org.overviewproject.tree.orm.DocumentProcessingError
import models.orm.DocumentSetUser

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

  implicit object JsonDocument extends Writes[Document] {
    override def writes(document: Document): JsValue = {
      val m = views.ScopedMessages("views.Document.show.title")
      
      val documentValues = Map(
        "id" -> toJson(document.id),
        "description" -> toJson(document.description),
        "tagids" -> toJson(document.tags),
        "documentcloud_id" -> toJson(document.documentCloudId),
        "nodeids" -> toJson(document.nodes),
        "title" -> toJson(document.title.getOrElse(m("empty_title")))) //
        
        

      toJson(documentValues)
    }
  }
  
  private def maybeMap(key: String, maybeValue: Option[String], toValue: String => String = identity): Map[String, JsValue] =
    maybeValue.map(v => Map(key -> toJson(toValue(v)))).getOrElse(Map.empty)
}
