package views.json.DocumentSet

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import models.DocumentSetCreationJobQueue.Position
import models.orm.{DocumentSet,DocumentSetCreationJob}

object show {
  private def m(key: String, args: Any*) = {
    play.api.i18n.Messages("views.DocumentSet._documentSet." + key, args: _*)
  }

  private def documentSetCreationJobProperties(job: DocumentSetCreationJob, queuePos: Position)(implicit lang: Lang) = {
    val notCompleteMap = Map(
      "state" -> toJson(m("job_state." + job.state.toString)),
      "percent_complete" -> toJson(math.round(job.fractionComplete * 100)),
      "state_description" -> toJson(job.stateDescription)
    )

    val notStartedMap = job.state match {
      case DocumentSetCreationJob.State.NotStarted => Map(
	"queue_position" -> toJson(Map("pos" -> queuePos.position, "length" -> queuePos.length)))
      case _ => Map()
    }

    notCompleteMap ++ notStartedMap
  }

  private[DocumentSet] def documentSetProperties(documentSet: DocumentSet)(implicit lang: Lang) = {
    Map(
      "html" -> toJson(views.html.DocumentSet._documentSet(documentSet).toString)
    )
  }

  private[DocumentSet] implicit def documentSetToJson(documentSet: DocumentSet, queuePos: Position) : JsValue = {
    val map = Map(
      "id" -> toJson(documentSet.id),
      "query" -> toJson(documentSet.query)
    ) ++ (documentSet.documentSetCreationJob.map(documentSetCreationJobProperties(_, queuePos))
          .getOrElse(documentSetProperties(documentSet)))

    toJson(map)
  }

  def apply(documentSet: DocumentSet, queuePos: Position) : JsValue = {
    documentSetToJson(documentSet, queuePos)
  }
}
