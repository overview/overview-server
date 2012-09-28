package views.json.DocumentSet

import models.orm.{ DocumentSet, DocumentSetCreationJob }
import models.orm.DocumentSetCreationJobState._
import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import views.Magic._
import views.ScopedMessages

object show {
  private val KeyScope = "views.DocumentSet._documentSet"
  private val StateKeyScope = "models.DocumentSetCreationJob.state"
  private val JobStateDescriptionKey = "job_state_description."
  private val m = ScopedMessages(KeyScope)
  private val stateM = ScopedMessages(StateKeyScope)

  private def stateDescription(description: String): String = {
    val keyWithArg = """([^:]*):(.*)""".r

    description match {
      case keyWithArg(key, arg) => m(JobStateDescriptionKey + key, arg)
      case "" => ""
      case d => m(JobStateDescriptionKey + d)
    }
  }

  private def documentSetCreationJobProperties(job: DocumentSetCreationJob)(implicit lang: Lang) = {
    val notCompleteMap = Map(
      "state" -> toJson(stateM(job.state.toString)),
      "percent_complete" -> toJson(math.round(job.fractionComplete * 100)),
      "state_description" -> toJson(stateDescription(job.stateDescription)))
    val notStartedMap = job.state match {
      case NotStarted => Map("n_jobs_ahead_in_queue" -> toJson(job.jobsAheadInQueue))
      case _ => Map()
    }

    notCompleteMap ++ notStartedMap
  }

  private[DocumentSet] def documentSetProperties(documentSet: DocumentSet)(implicit lang: Lang) = {
    Map(
      "html" -> toJson(views.html.DocumentSet._documentSet(documentSet).toString))
  }

  private[DocumentSet] implicit def documentSetToJson(documentSet: DocumentSet): JsValue = {
    val documentSetMap = Map(
      "id" -> toJson(documentSet.id),
      "query" -> toJson(documentSet.query))

    val jobStatusMap = documentSet.documentSetCreationJob match {
      case Some(documentSetCreationJob) => documentSetCreationJobProperties(documentSetCreationJob)
      case None => documentSetProperties(documentSet)
    }

    toJson(documentSetMap ++ jobStatusMap)
  }

  def apply(documentSet: DocumentSet): JsValue = {
    documentSetToJson(documentSet)
  }
}
