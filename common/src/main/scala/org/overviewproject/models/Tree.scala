package org.overviewproject.models

import java.sql.Timestamp
import org.overviewproject.tree.orm.{Tree=>DeprecatedTree}

case class Tree(
  val id: Long,
  val documentSetId: Long,
  val rootNodeId: Long,
  val jobId: Long,
  val title: String,
  val documentCount: Int,
  val lang: String,
  val description: String,
  val suppliedStopWords: String,
  val importantWords: String,
  val createdAt: Timestamp
) {
  def update(attributes: Tree.UpdateAttributes) = copy(title=attributes.title)

  def toDeprecatedTree: DeprecatedTree = DeprecatedTree(
    id,
    documentSetId,
    rootNodeId,
    jobId,
    title,
    documentCount,
    lang,
    description,
    suppliedStopWords,
    importantWords,
    createdAt
  )
}

object Tree {
  case class UpdateAttributes(
    title: String
  )
}
