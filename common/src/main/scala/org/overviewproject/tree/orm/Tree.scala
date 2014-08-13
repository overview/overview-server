package org.overviewproject.tree.orm

import java.sql.Timestamp
import org.squeryl.KeyedEntity
import scala.collection.mutable.Buffer

import org.overviewproject.models.VizLike

case class Tree(
    id: Long,
    override val documentSetId: Long,
    rootNodeId: Long,
    override val jobId: Long,
    override val title: String,
    override val documentCount: Int,
    lang: String,
    description: String = "",
    suppliedStopWords: String = "",
    importantWords: String = "",
    override val createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime))
  extends KeyedEntity[Long] with DocumentSetComponent with VizLike {

  override def isPersisted(): Boolean = (id > 0)

  override def creationData = {
    val buffer = Buffer(
      "jobId" -> jobId.toString,
      "nDocuments" -> documentCount.toString,
      "rootNodeId" -> rootNodeId.toString,
      "lang" -> lang
    )
    if (description.length > 0) buffer += ("description" -> description)
    if (suppliedStopWords.length > 0) buffer += ("suppliedStopWords" -> suppliedStopWords)
    if (importantWords.length > 0) buffer += ("importantWords" -> importantWords)
    buffer.toIterable
  }
}
