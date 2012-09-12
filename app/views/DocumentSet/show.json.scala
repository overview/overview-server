package views.json.DocumentSet

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import models.orm.{DocumentSet,DocumentSetCreationJob}
import models.orm.DocumentSetCreationJobState._


object show {
  private def m(key: String, args: Any*) = {
    play.api.i18n.Messages("views.DocumentSet._documentSet." + key, args: _*)
  }

  private def documentSetCreationJobProperties(job: DocumentSetCreationJob)(implicit lang: Lang) = {
    val notCompleteMap = Map(
      "state" -> toJson(m("job_state." + job.state.toString)),
      "percent_complete" -> toJson(math.round(job.fractionComplete * 100)),
      "state_description" -> toJson(job.stateDescription)
    )

    val notStartedMap = job.state match {
      case NotStarted => Map("n_jobs_ahead_in_queue" -> toJson(job.jobsAheadInQueue))
      case _ => Map()
    }

    notCompleteMap ++ notStartedMap
  }

  private[DocumentSet] def documentSetProperties(documentSet: DocumentSet)(implicit lang: Lang) = {
    Map(
      "html" -> toJson(views.html.DocumentSet._documentSet(documentSet).toString)
    )
  }

  private[DocumentSet] implicit def documentSetToJson(documentSet: DocumentSet) : JsValue = {
    val map = Map(
      "id" -> toJson(documentSet.id),
      "query" -> toJson(documentSet.query)
    ) ++ (documentSet.documentSetCreationJob.map(documentSetCreationJobProperties)
          .getOrElse(documentSetProperties(documentSet)))

    toJson(map)
  }

  def apply(documentSet: DocumentSet) : JsValue = {
    documentSetToJson(documentSet)
  }
}
