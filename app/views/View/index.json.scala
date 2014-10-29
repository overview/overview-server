package views.json.View

import java.util.Date
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone
import play.api.libs.json.{Json,JsValue,JsArray}
import scala.collection.mutable.ArrayBuffer

import org.overviewproject.tree.orm.{DocumentSetCreationJob,DocumentSetCreationJobState,Tree}
import org.overviewproject.models.View
import views.helper.DocumentSetHelper.jobDescriptionKeyToMessage

object index {
  private val iso8601Format = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC)
  private def dateToISO8601(time: Date) : String = iso8601Format.print(time.getTime())

  private[View] def jobToJson(job: DocumentSetCreationJob) : JsValue = {
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

  private[View] def treeToJson(tree: Tree): JsValue = {
    val creationData = tree.creationData.map((x: (String,String)) => Json.arr(x._1, x._2))

    Json.obj(
      "type" -> "tree",
      "id" -> tree.id,
      "jobId" -> tree.jobId,
      "nDocuments" -> tree.documentCount,
      "title" -> tree.title,
      "createdAt" -> dateToISO8601(tree.createdAt),
      "creationData" -> creationData.toSeq
    )
  }

  private[View] def viewToJson(view: View): JsValue = {
    Json.obj(
      "type" -> "view",
      "id" -> view.id,
      "title" -> view.title,
      "url" -> view.url,
      "apiToken" -> view.apiToken,
      "createdAt" -> dateToISO8601(view.createdAt),
      "creationData" -> Json.obj()
    )
  }

  def apply(trees: Iterable[Tree], views: Iterable[View], jobs: Iterable[DocumentSetCreationJob]): JsValue = {
    val values = trees.map(treeToJson) ++ views.map(viewToJson) ++ jobs.map(jobToJson)
    JsArray(values.toSeq)
  }
}
