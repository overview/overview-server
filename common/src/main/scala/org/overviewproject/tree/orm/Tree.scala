package org.overviewproject.tree.orm

import java.sql.Timestamp
import org.squeryl.KeyedEntity

import org.overviewproject.models.Viz

case class Tree(
    id: Long,
    documentSetId: Long,
    title: String,
    documentCount: Int,
    lang: String,
    description: String = "",
    suppliedStopWords: String = "",
    importantWords: String = "",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)) extends KeyedEntity[Long] with DocumentSetComponent with Viz {

  override def isPersisted(): Boolean = (id > 0)
}
