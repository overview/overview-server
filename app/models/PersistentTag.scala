package models

import java.sql.Connection

trait PersistentTag {
  val id: Long
  val name: String
  val color: Option[String]

  def documentIds(implicit c: Connection): models.core.DocumentIdList

  def count(implicit c: Connection): Long
  def countsPerNode(nodeIds: Seq[Long])(implicit c: Connection): Seq[(Long, Long)]
  def update(newName: String, newColor: String)(implicit c: Connection): Int
  def delete()(implicit c: Connection): Long
  def loadTag(implicit c: Connection): core.Tag
  def loadDocuments(implicit c: Connection): Seq[core.Document]
}

object PersistentTag {

  def findOrCreateByName(name: String, documentSetId: Long,
    loader: PersistentTagLoader = new PersistentTagLoader(),
    parser: DocumentListParser = new DocumentListParser(),
    saver: PersistentTagSaver = new PersistentTagSaver())(implicit c: Connection): PersistentTag = {

    val tag = models.orm.Tag.findByName(documentSetId, name) match {
      case Some(t) => t
      case None => models.orm.Tag(documentSetId = documentSetId, name = name).save
    }

    new PersistentTagImpl(tag, loader, parser, saver)
  }

  def findByName(name: String, documentSetId: Long,
    loader: PersistentTagLoader = new PersistentTagLoader(),
    parser: DocumentListParser = new DocumentListParser(),
    saver: PersistentTagSaver = new PersistentTagSaver())(implicit c: Connection): Option[PersistentTag] = {

    models.orm.Tag.findByName(documentSetId, name).map(new PersistentTagImpl(_, loader, parser, saver))
  }

  private class PersistentTagImpl(tag: models.orm.Tag,
    loader: PersistentTagLoader,
    parser: DocumentListParser,
    saver: PersistentTagSaver) extends DocumentListLoader(loader, parser) with PersistentTag {

    val id = tag.id
    val name = tag.name
    val color = tag.color

    def documentIds(implicit c: Connection): models.core.DocumentIdList = {
      val t = loadTag
      t.documentIds
    }

    def count(implicit c: Connection): Long = {
      loader.countDocuments(id)
    }

    def countsPerNode(nodeIds: Seq[Long])(implicit c: Connection): Seq[(Long, Long)] = {
      loader.countsPerNode(nodeIds, id)
    }

    def update(newName: String, newColor: String)(implicit c: Connection): Int = {
      saver.update(id, newName, newColor)
    }

    def delete()(implicit c: Connection): Long = {
      saver.delete(id)
    }

    def loadTag(implicit c: Connection): core.Tag = {
      val tagData = loader.loadTag(id)
      parser.createTags(tagData).head
    }

    def loadDocuments(implicit c: Connection): Seq[core.Document] = {
      val documentIdList = documentIds.firstIds
      loadDocumentList(documentIdList)
    }

  }
}
