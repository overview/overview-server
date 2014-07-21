package views.json.DocumentSet

import java.util.Date
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone
import play.api.libs.json.{Json, JsValue}

import org.overviewproject.tree.orm.{DocumentSet,DocumentSetCreationJob,SearchResult,Tag}
import org.overviewproject.models.Viz

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
    vizs: Iterable[Viz],
    vizJobs: Iterable[DocumentSetCreationJob],
    tags: Iterable[Tag],
    searchResults: Iterable[SearchResult]) : JsValue = {

    Json.obj(
      "nDocuments" -> documentSet.documentCount,
      "vizs" -> views.json.Viz.index(vizs, vizJobs),
      "searchResults" -> searchResults.map(views.json.SearchResult.show(_)),
      "tags" -> tags.map(writeTag)
    )
  }
}
