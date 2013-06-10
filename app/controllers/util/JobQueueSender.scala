package controllers.util

import org.overviewproject.jobs.models.Search
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.toJson
import play.api.Play.current
import plugins.StompPlugin
import com.typesafe.plugin.use

object JobQueueSender {
  implicit val searchArgWrites: Writes[Search] = (
    (__ \ "documentSetId").write[Long] and
    (__ \ "query").write[String]
  )(unlift(Search.unapply))
  
  def send(search: Search): Either[Unit, Unit] = {
    val jsonMessage = toJson(Map(
      "cmd" -> toJson("search"),
      "args" -> toJson(search)
    ))
    
    val connection = use[StompPlugin].queueConnection
    
    
    connection.send(jsonMessage.toString)
  }
}