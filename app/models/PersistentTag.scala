package models

import java.sql.Connection

trait PersistentTag {
  val documentIds: models.core.DocumentIdList

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
