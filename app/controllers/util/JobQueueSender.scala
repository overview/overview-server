package controllers.util

import com.typesafe.plugin.use
import play.api.Play.current
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Json.toJson
import plugins.StompPlugin
import org.overviewproject.jobs.models._


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
    implicit val deleteArgWrites: Writes[Delete] = (
      (__ \ "documentSetId").write[Long] and
      (__ \ "waitForJobRemoval").write[Boolean])(unlift(Delete.unapply))

    val jsonMessage = toJson(Map(
      "cmd" -> toJson("delete"),
      "args" -> toJson(delete)))
      
    sendMessageToGroup(jsonMessage, delete.documentSetId)
  }

  /**
   * Send a `DeleteTreeJob` message to the message queue.
   * @return a `Left[Unit]` if the connection queue is down. `Right[Unit]` otherwise.
   */
  def send(deleteTreeJob: DeleteTreeJob): Either[Unit, Unit] = {
    val jsonMessage = toJson(Map(
      "cmd" -> toJson("delete_tree_job"),
      "args" -> toJson(Map(
        "documentSetId" -> deleteTreeJob.documentSetId          ))
    ))
    
    sendMessageToGroup(jsonMessage, deleteTreeJob.documentSetId)
  }

  /**
   * Send a `ClusterFileGroup` message to the Clustering message queue.
   * @return a `Left[Unit]` if the connection queue is down. `Right[Unit]` otherwise.
   */
  def send(clusterFileGroup: ClusterFileGroup): Either[Unit, Unit] = {
    implicit val clusterFileGroupWrites: Writes[ClusterFileGroup] = (
      (__ \ "documentSetId").write[Long] and
      (__ \ "fileGroupId").write[Long] and
      (__ \ "title").write[String] and
      (__ \ "lang").write[String] and
      (__ \ "splitDocuments").write[Boolean] and
      (__ \ "suppliedStopWords").write[String] and
      (__ \ "importantWords").write[String])(unlift(ClusterFileGroup.unapply))

    val jsonMessage = toJson(Map(
      "cmd" -> toJson("cluster_file_group"),
      "args" -> toJson(clusterFileGroup)))

    sendMessageToClusteringQueue(jsonMessage)
  }

  /**
   * Send a `CancelFileUpload` message to the Clustering message queue.
   */
  def send(cancelFileUpload: CancelFileUpload): Either[Unit, Unit] = {
    implicit val cancelFileUploadWrites: Writes[CancelFileUpload] = (
      (__ \ "documentSetId").write[Long] and
      (__ \ "fileGroupId").write[Long])(unlift(CancelFileUpload.unapply))
      
    val jsonMessage = toJson(Map(
      "cmd" -> toJson("cancel_file_upload"),
      "args" -> toJson(cancelFileUpload)))

    sendMessageToClusteringQueue(jsonMessage)
  }

  
  private def sendMessageToGroup(jsonMessage: JsValue, documentSetId: Long): Either[Unit, Unit] = {
    val connection = use[StompPlugin].documentSetCommandQueue

    connection.send(jsonMessage.toString, s"$documentSetId")
  }

  private def sendMessageToFileGroupJobQueue(jsonMessage: JsValue): Either[Unit, Unit] = {
    val connection = use[StompPlugin].fileGroupCommandQueue

    connection.send(jsonMessage.toString)
  }

  private def sendMessageToClusteringQueue(jsonMessage: JsValue): Either[Unit, Unit] = {
    val connection = use[StompPlugin].clusteringCommandQueue

    connection.send(jsonMessage.toString)
  }
}

