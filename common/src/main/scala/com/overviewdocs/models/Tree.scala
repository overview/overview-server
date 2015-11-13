package com.overviewdocs.models

import java.sql.Timestamp

case class Tree(
  val id: Long,
  val documentSetId: Long,
  val rootNodeId: Option[Long],
  val title: String,
  val documentCount: Option[Int],
  val lang: String,
  val description: String,
  val suppliedStopWords: String,
  val importantWords: String,
  val createdAt: Timestamp,
  val tagId: Option[Long],
  val progress: Double,
  val progressDescription: String,
  val cancelled: Boolean
) {
  def update(attributes: Tree.UpdateAttributes) = copy(title=attributes.title)
}

object Tree {
  case class UpdateAttributes(
    title: String
  )

  case class CreateAttributes(
    val documentSetId: Long,
    val rootNodeId: Option[Long] = None,
    val title: String = "Tree", // TODO nix this default and always choose a good title
    val documentCount: Option[Int] = None,
    val lang: String,
    val description: String = "",
    val suppliedStopWords: String = "",
    val importantWords: String = "",
    val createdAt: Timestamp = new Timestamp(new java.util.Date().getTime),
    val tagId: Option[Long] = None,
    val progress: Double = 0.0,
    val progressDescription: String = "",
    val cancelled: Boolean = false
  ) {
    def toTreeWithId(id: Long): Tree = Tree(
      id,
      documentSetId,
      rootNodeId,
      title,
      documentCount,
      lang,
      description,
      suppliedStopWords,
      importantWords,
      createdAt,
      tagId,
      progress,
      progressDescription,
      cancelled
    )
  }
}
