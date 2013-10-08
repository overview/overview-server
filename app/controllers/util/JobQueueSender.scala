package controllers.util

import org.overviewproject.jobs.models.Search
import com.typesafe.plugin.use
import play.api.Play.current
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Json.toJson
import plugins.StompPlugin
import org.overviewproject.jobs.models.Delete
import org.overviewproject.jobs.models.ProcessGroupedFileUpload
import org.overviewproject.jobs.models.ProcessGroupedFileUpload

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

    sendMessageToGroup(jsonMessage, search.documentSetId)
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

    sendMessageToGroup(jsonMessage, delete.documentSetId)
  }

  /**
   * Send a `ProcessGroupedFileUpload` message to the FileGroup message queue.
   * @return a `Left[Unit]` if the connection queue is down. `Right[Unit]` otherwise.
   */
  def send(processUpload: ProcessGroupedFileUpload): Either[Unit, Unit] = {
    implicit val processUploadArgWrites: Writes[ProcessGroupedFileUpload] = (
      (__ \ "fileGroupId").write[Long] and
      (__ \ "uploadedFileId").write[Long])(unlift(ProcessGroupedFileUpload.unapply))

    val jsonMessage = toJson(Map(
      "cmd" -> toJson("process_file"),
      "args" -> toJson(processUpload)))

    sendMessageToFileGroupJobQueue(jsonMessage)
  }

  private def sendMessageToGroup(jsonMessage: JsValue, documentSetId: Long): Either[Unit, Unit] = {
    val connection = use[StompPlugin].documentSetCommandQueue

    connection.send(jsonMessage.toString, s"$documentSetId")
  }

  private def sendMessageToFileGroupJobQueue(jsonMessage: JsValue): Either[Unit, Unit] = {
    val connection = use[StompPlugin].fileGroupCommandQueue

    connection.send(jsonMessage.toString)
  }
}

