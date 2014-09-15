package org.overviewproject.tree.orm

import java.sql.Timestamp
import org.squeryl.KeyedEntity
import scala.collection.mutable.Buffer

case class Tree(
  id: Long,
  val documentSetId: Long,
  rootNodeId: Long,
  val jobId: Long,
  val title: String,
  val documentCount: Int,
  lang: String,
  description: String = "",
  suppliedStopWords: String = "",
  importantWords: String = "",
  val createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)
) extends KeyedEntity[Long] with DocumentSetComponent {

  override def isPersisted(): Boolean = (id > 0)

  def creationData = {
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
