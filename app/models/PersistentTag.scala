package models

import java.sql.Connection

trait PersistentTag {
  val id: Long

  def count(implicit c: Connection): Long
  def countsPerNode(nodeIds: Seq[Long])(implicit c: Connection): Seq[(Long, Long)]
  def update(name: String, color: String)(implicit c: Connection): Int
  def delete()(implicit c: Connection): Long
  def loadTag(implicit c: Connection): core.Tag
  def loadDocuments(tag: core.Tag)(implicit c: Connection): Seq[core.Document]
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

    new PersistentTagImpl(tag, name, loader, parser, saver)
  }

  def findByName(name: String, documentSetId: Long,
    loader: PersistentTagLoader = new PersistentTagLoader(),
    parser: DocumentListParser = new DocumentListParser(),
    saver: PersistentTagSaver = new PersistentTagSaver())(implicit c: Connection): Option[PersistentTag] = {

    models.orm.Tag.findByName(documentSetId, name).map(new PersistentTagImpl(_, name, loader, parser, saver))
  }

  private class PersistentTagImpl(tag: models.orm.Tag, name: String,
    loader: PersistentTagLoader,
    parser: DocumentListParser,
    saver: PersistentTagSaver) extends DocumentListLoader(loader, parser) with PersistentTag {
    val id = tag.id

    def count(implicit c: Connection): Long = {
      loader.countDocuments(id)
    }

    def countsPerNode(nodeIds: Seq[Long])(implicit c: Connection): Seq[(Long, Long)] = {
      loader.countsPerNode(nodeIds, id)
    }

    def update(name: String, color: String)(implicit c: Connection): Int = {
      saver.update(id, name, color)
    }

    def delete()(implicit c: Connection): Long = {
      saver.delete(id)
    }

    def loadTag(implicit c: Connection): core.Tag = {
      val tagData = loader.loadTag(id)
      parser.createTags(tagData).head
    }

    def loadDocuments(tag: core.Tag)(implicit c: Connection): Seq[core.Document] = {
      val documentIds = tag.documentIds.firstIds
      loadDocumentList(documentIds)
    }

  }
}
