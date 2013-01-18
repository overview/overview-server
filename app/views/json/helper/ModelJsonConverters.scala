package views.json.helper

import models.PersistentTagInfo
import models.core.{ Document, DocumentIdList }
import play.api.i18n.Lang
import play.api.libs.json.{ JsValue, Writes }
import play.api.libs.json.Json.toJson
import org.overviewproject.tree.orm.DocumentProcessingError

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
  
  implicit object JsonDocumentProcessingError extends Writes[DocumentProcessingError] {
    override def writes(documentProcessingError: DocumentProcessingError): JsValue = {
      val m = views.ScopedMessages("views.DocumentProcessingErrorList.show.message")
      
      val message = documentProcessingError.statusCode match {
        case Some(403) => m("access_denied")
        case Some(404) => m("not_found")
        case Some(_) => m("server_error")
        case None => m("internal_error")
      } 
      
      toJson(Map(
          "text_url" -> documentProcessingError.textUrl,
          "message" -> message))
          
    }
  }
  
  private def maybeMap(key: String, maybeValue: Option[String], toValue: String => String = identity): Map[String, JsValue] =
    maybeValue.map(v => Map(key -> toJson(toValue(v)))).getOrElse(Map.empty)
    

}
