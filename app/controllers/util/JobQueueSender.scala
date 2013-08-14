package controllers.util

import org.overviewproject.jobs.models.Search
import com.typesafe.plugin.use
import play.api.Play.current
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Json.toJson
import plugins.StompPlugin
import org.overviewproject.jobs.models.Delete

/**
 * Converts a message to a search query and sends it to the message queue connection.
 * TODO: Work with other types of messages (when we have other types of messages)
 */
object JobQueueSender {
  implicit val searchArgWrites: Writes[Search] = (
    (__ \ "documentSetId").write[Long] and
    (__ \ "query").write[String])(unlift(Search.unapply))

  /**
   * Send the message to the message queue.
   * @return a `Left[Unit]` if the connection to the queue is down, `Right[Unit]` otherwise.
   */
  def send(search: Search): Either[Unit, Unit] = {

    val jsonMessage = toJson(Map(
      "cmd" -> toJson("search"),
      "args" -> toJson(search)))

    val connection = use[StompPlugin].queueConnection

    connection.send(jsonMessage.toString)
  }

  /**
   * Send a `Delete` message to the message queue.
   * @return a `Left[Unit]` if the connection queue is down. `Right[Unit]` otherwise.
   */
  def send(delete: Delete): Either[Unit, Unit] = {
    val jsonMessage = toJson(Map(
      "cmd" -> toJson("delete"),
      "args" -> toJson(Map(
        "documentSetId" -> delete.documentSetId))))

    val connection = use[StompPlugin].queueConnection

    connection.send(jsonMessage.toString)
  }
}

