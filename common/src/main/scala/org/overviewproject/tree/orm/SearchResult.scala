package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity
import java.sql.Timestamp

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
  createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime),
  override val id: Long = 0
) extends KeyedEntity[Long] with DocumentSetComponent {
  override def isPersisted(): Boolean = (id > 0)
  
  def this() = this(state = SearchResultState.Error, documentSetId = 0l, query = "")
}
