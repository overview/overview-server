package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity

object SearchResultState extends Enumeration {
  type SearchResultState = Value
  
  val Complete = Value(1, "Complete")
  val InProgress = Value(2, "InProgress")
  val Error = Value(3, "Error")
  
}

case class SearchResult (
  state: SearchResultState.Value,
  documentSetId: Long,
  query: String,
  override val id: Long = 0
) extends KeyedEntity[Long] {
  override def isPersisted(): Boolean = (id > 0)
  
  def this() = this(state = SearchResultState.Error, documentSetId = 0l, query = "")
}
