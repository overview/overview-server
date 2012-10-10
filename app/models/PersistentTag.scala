/*
 * PersistentTag.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package models

import java.sql.Connection

/** Tag attributes needed by the TreeView client */
trait PersistentTagInfo {
  val id: Long
  val name: String
  val color: Option[String]
  val documentIds: models.core.DocumentIdList
}

/** Methods that require database queries */
trait PersistentTag extends PersistentTagInfo {
  /** @return number of documents tagged */
  def count(implicit c: Connection): Long

  /**
   * @return a collection of (nodeId, count) tuples, indicating how many documents
   * are tagged in that node
   */
  def countsPerNode(nodeIds: Seq[Long])(implicit c: Connection): Seq[(Long, Long)]

  /**
   * @return document information for the first 10 tagged documents
   */
  def loadDocuments(implicit c: Connection): Seq[core.Document]
}

/**
 * Creates instances of PersistentTags, providing default loader and parser for
 * accessing the database and interpreting the result
 */
object PersistentTag {

  /** Factory method for PersistentTags. Currently requires an OverviewTag as a parameter */
  def apply(tag: OverviewTag,
    loader: PersistentTagLoader = new PersistentTagLoader(),
    parser: DocumentListParser = new DocumentListParser())(implicit c: Connection): PersistentTag = {
    // the PersistentTagImpl needs to be created with an implicit connection, so it can
    // load the documentIdList
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
      val documentIdList = documentIds.firstIds // already loaded, so query is run just once
      loadDocumentList(documentIdList)
    }

  }
}
