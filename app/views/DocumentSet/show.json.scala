package views.json.DocumentSet

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import models.orm.{DocumentSetCreationJob }
import models.orm.DocumentSetCreationJobState.NotStarted
import models.OverviewDocumentSet
import views.ScopedMessages
import views.helper.DocumentSetHelper

object show {
  private val jobStateKeyToMessage = ScopedMessages("models.DocumentSetCreationJob.state")

  private def documentSetCreationJobProperties(job: DocumentSetCreationJob)(implicit lang: Lang) = {
    val notCompleteMap = Map(
      "state" -> toJson(jobStateKeyToMessage(job.state.toString)),
      "percent_complete" -> toJson(math.round(job.fractionComplete * 100)),
      "state_description" -> toJson(DocumentSetHelper.jobDescriptionKeyToMessage(job.statusDescription)))
    val notStartedMap = job.state match {
      case NotStarted => Map("n_jobs_ahead_in_queue" -> toJson(job.jobsAheadInQueue))
      case _ => Map()
    }

    notCompleteMap ++ notStartedMap
  }

  private[DocumentSet] def documentSetProperties(documentSet: OverviewDocumentSet)(implicit lang: Lang) = {
    Map("html" -> toJson(views.html.DocumentSet._documentSet(documentSet).toString))
  }

  private[DocumentSet] implicit def documentSetToJson(documentSet: OverviewDocumentSet): JsValue = {
    val documentSetMap = Map("id" -> toJson(documentSet.id))

    val jobStatusMap = documentSet.creationJob match {
      case Some(documentSetCreationJob) => documentSetCreationJobProperties(documentSetCreationJob)
      case None => documentSetProperties(documentSet)
    }

    toJson(documentSetMap ++ jobStatusMap)
  }

  def apply(documentSet: OverviewDocumentSet): JsValue = {
    documentSetToJson(documentSet)
  }
}
