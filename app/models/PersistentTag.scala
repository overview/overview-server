package models

import java.sql.Connection

trait PersistentTagInfo {
  val id: Long
  val name: String
  val color: Option[String]
  val documentIds: models.core.DocumentIdList
}

trait PersistentTag extends PersistentTagInfo {
  def count(implicit c: Connection): Long
  def countsPerNode(nodeIds: Seq[Long])(implicit c: Connection): Seq[(Long, Long)]
  def loadDocuments(implicit c: Connection): Seq[core.Document]
}

object PersistentTag {

  def apply(tag: OverviewTag,
    loader: PersistentTagLoader = new PersistentTagLoader(),
    parser: DocumentListParser = new DocumentListParser())(implicit c: Connection): PersistentTag = {

    new PersistentTagImpl(tag, loader, parser)
  }

  private class PersistentTagImpl(tag: models.OverviewTag,
    loader: PersistentTagLoader,
    parser: DocumentListParser)(implicit c: Connection) extends DocumentListLoader(loader, parser) with PersistentTag {

    val id: Long = tag.id
      val name: String = tag.name
      val color: Option[String] = tag.withColor.map(_.color)
    val documentIds = loadDocumentIds(tag.id)
      
    private def loadDocumentIds(id: Long)(implicit c: Connection): models.core.DocumentIdList = {
      val documentListData = loader.loadDocumentList(id)
      parser.createDocumentIdList(documentListData)
    }

    def count(implicit c: Connection): Long = {
      loader.countDocuments(tag.id)
    }

    def countsPerNode(nodeIds: Seq[Long])(implicit c: Connection): Seq[(Long, Long)] = {
      loader.countsPerNode(nodeIds, tag.id)
    }

    def loadDocuments(implicit c: Connection): Seq[core.Document] = {
      val documentIdList = documentIds.firstIds
      loadDocumentList(documentIdList)
    }

  }
}
