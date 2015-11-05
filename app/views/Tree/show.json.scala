package views.json.Tree

import java.util.Date
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone
import play.api.libs.json.{Json,JsValue}
import scala.collection.mutable.Buffer

import com.overviewdocs.models.Tree

object show {
  private val iso8601Format = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC)
  private def dateToISO8601(time: Date) : String = iso8601Format.print(time.getTime())

  def apply(tree: Tree): JsValue = {
    val buffer = Buffer(
      "jobId" -> tree.jobId.toString,
      "nDocuments" -> tree.documentCount.toString,
      "rootNodeId" -> tree.rootNodeId.toString,
      "lang" -> tree.lang
    )
    if (tree.description.length > 0) buffer += ("description" -> tree.description)
    if (tree.suppliedStopWords.length > 0) buffer += ("suppliedStopWords" -> tree.suppliedStopWords)
    if (tree.importantWords.length > 0) buffer += ("importantWords" -> tree.importantWords)

    val creationData = buffer.map((x: (String,String)) => Json.arr(x._1, x._2))

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
}
