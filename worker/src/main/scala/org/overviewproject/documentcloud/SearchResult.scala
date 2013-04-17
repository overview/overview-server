package org.overviewproject.documentcloud

import play.api.libs.json.Json

/** Information about a document stored by DocumentCloud */
case class Document(id: String, title: String, access: String, canonical_url: String)
/** Information from a DocumentCloud Search Result */
case class SearchResult(total: Int, page: Int, documents: Seq[Document])


/** Convert the JSON received when doing a DocumentCloud query to the above classes */
object ConvertSearchResult {

  implicit private val documentReads = Json.reads[Document]
  implicit private val searchResultReads = Json.reads[SearchResult]

  def apply(jsonSearchResult: String): SearchResult = Json.parse(jsonSearchResult).as[SearchResult]
}