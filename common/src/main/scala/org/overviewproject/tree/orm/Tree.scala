package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity
import java.sql.Timestamp

case class Tree(
    id: Long,
    documentSetId: Long,
    title: String,
    documentCount: Int,
    lang: String,
    suppliedStopWords: String = "",
    importantWords: String = "",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)) extends KeyedEntity[Long] with DocumentSetComponent {

  override def isPersisted(): Boolean = (id > 0)
}
