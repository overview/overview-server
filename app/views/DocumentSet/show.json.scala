package views.json.DocumentSet

import java.util.Date
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone
import play.api.i18n.Messages
import play.api.libs.json.{Json, JsValue}

import com.overviewdocs.tree.orm.{DocumentSetCreationJob,Tag,Tree}
import com.overviewdocs.models.{DocumentSet,View}

object show {
  private val iso8601Format = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC)

  private def dateToISO8601(time: Date) : String = {
    iso8601Format.print(time.getTime())
  }

  private def writeTag(tag: Tag) : JsValue = {
    Json.obj(
      "id" -> tag.id,
      "name" -> tag.name,
      "color" -> ("#" + tag.color)
    )
  }

  def apply(
    documentSet: DocumentSet,
    trees: Iterable[Tree],
    _views: Iterable[View],
    viewJobs: Iterable[DocumentSetCreationJob],
    tags: Iterable[Tag]
  )(implicit messages: Messages): JsValue = Json.obj(
    "name" -> documentSet.title,
    "nDocuments" -> documentSet.documentCount,
    "metadataSchema" -> documentSet.metadataSchema.toJson,
    "views" -> views.json.View.index(trees, _views, viewJobs),
    "tags" -> tags.map(writeTag)
  )
}
