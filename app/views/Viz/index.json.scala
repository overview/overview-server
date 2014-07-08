package views.json.Viz

import java.util.Date
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone
import play.api.libs.json.{Json,JsValue,JsArray}
import scala.collection.mutable.ArrayBuffer

import org.overviewproject.models.Viz
import org.overviewproject.tree.orm.{DocumentSetCreationJob,DocumentSetCreationJobState}
import views.helper.DocumentSetHelper.jobDescriptionKeyToMessage

object index {
  private val iso8601Format = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC)
  private def dateToISO8601(time: Date) : String = iso8601Format.print(time.getTime())

  private[Viz] def jobToJson(job: DocumentSetCreationJob) : JsValue = {
    val creationData = new ArrayBuffer[Seq[String]]
    creationData += Seq("lang", job.lang)

    val description = job.treeDescription.getOrElse("")
    if (description.length > 0) creationData += Seq("description", description)
    if (job.suppliedStopWords.length > 0) creationData += Seq("suppliedStopWords", job.suppliedStopWords)
    if (job.importantWords.length > 0) creationData += Seq("importantWords", job.importantWords)

    Json.obj(
      "type" -> (if (job.state == DocumentSetCreationJobState.Error) "error" else "job"),
      "id" -> job.id,
      "title" -> job.treeTitle.getOrElse(throw new Exception("job missing treeTitle")),
      "progress" -> Json.obj(
        "fraction" -> job.fractionComplete,
        "state" -> job.state.toString,
        "description" -> jobDescriptionKeyToMessage(job.statusDescription)
      ),
      "creationData" -> creationData
    )
  }

  private[Viz] def vizToJson(viz: Viz) : JsValue = {
    val creationData = viz.creationData.map((x: (String,String)) => Json.arr(x._1, x._2))

    Json.obj(
      "type" -> "viz",
      "id" -> viz.id,
      "jobId" -> viz.jobId,
      "title" -> viz.title,
      "createdAt" -> dateToISO8601(viz.createdAt),
      "creationData" -> creationData.toSeq,
      "nDocuments" -> viz.documentCount
    )
  }

  def apply(vizs: Iterable[Viz], jobs: Iterable[DocumentSetCreationJob]): JsValue = {
    val values = jobs.map(jobToJson) ++ vizs.map(vizToJson)
    JsArray(values.toSeq)
  }
}
