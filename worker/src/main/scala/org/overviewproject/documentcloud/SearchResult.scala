package org.overviewproject.documentcloud

import play.api.libs.functional.syntax._
import play.api.libs.json._

/** Information about a document stored by DocumentCloud */
case class Document(id: String, title: String, access: String, pageUrlTemplate: String) {
  val url: String = s"https://www.documentcloud.org/api/documents/$id.txt"
}

/** Information from a DocumentCloud Search Result */
case class SearchResult(total: Int, page: Int, documents: Seq[Document])

/** Convert the JSON received when doing a DocumentCloud query to the above classes */
object ConvertSearchResult {

  implicit private val documentReads = (
    (__ \ "id").read[String] and
    (__ \ "title").read[String] and
    (__ \ "access").read[String] and
    (__ \ "resources" \ "page" \ "text").read[String])(Document)

  implicit private val searchResultReads = Json.reads[SearchResult]

  def apply(jsonSearchResult: String): SearchResult = Json.parse(jsonSearchResult).as[SearchResult]
}