package org.overviewproject.documentcloud

import play.api.libs.json.Json

case class Document(id: String, title: String, access: String, canonical_url: String)
case class SearchResult(total: Int, page: Int, documents: Seq[Document])

object ConvertSearchResult {

  implicit private val documentReads = Json.reads[Document]
  implicit private val searchResultReads = Json.reads[SearchResult]

  def apply(jsonSearchResult: String): SearchResult = Json.parse(jsonSearchResult).as[SearchResult]
}