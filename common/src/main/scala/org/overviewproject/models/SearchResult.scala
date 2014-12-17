package org.overviewproject.models

import java.sql.Timestamp

object SearchResultState extends Enumeration {
  type SearchResultState = Value
  
  val Complete = Value(1)
  val InProgress = Value(2)
  val Error = Value(3)
}

case class SearchResult(
  id: Long,
  documentSetId: Long,
  query: String,
  createdAt: Timestamp,
  state: SearchResultState.Value
)