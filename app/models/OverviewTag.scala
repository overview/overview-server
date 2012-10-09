package models

import java.sql.Connection
import models.orm.Tag

trait OverviewTag {
  val id: Long
  val name: String

  def withName(newName: String): OverviewTag
  def withColor(newColor: String): OverviewTag with TagColor
  def withColor: Option[OverviewTag with TagColor]

  def withDocumentInformation(implicit c: Connection): OverviewTag with TaggedDocumentInformation
  
  def save: Unit
}

trait TagColor {
  val color: String
}

trait TaggedDocumentInformation {
  val documentIds: models.core.DocumentIdList
}

  
case class PotentialTag(name: String) {

  def inDocumentSet(documentSetId: Long): Option[OverviewTag] = {
    OverviewTag.findByName(documentSetId, name)
  }

  def create(documentSetId: Long): OverviewTag = OverviewTag.create(documentSetId, name)

}

object OverviewTag {

  def findByName(documentSetId: Long, tagName: String): Option[OverviewTag] =
    Tag.findByName(documentSetId, tagName).map(new OverviewTagImpl(_))

  def create(documentSetId: Long, tagName: String): OverviewTag = {
    val newTag = Tag(documentSetId = documentSetId, name = tagName).save

    new OverviewTagImpl(newTag)

  }

  private[models] class OverviewTagImpl(tag: Tag) extends OverviewTag {
    private class ColoredTag(source: Tag, override val color: String) extends OverviewTagImpl(source.copy(color = Some(color))) with TagColor

    override val id: Long = tag.id
    override val name: String = tag.name

    override def withName(newName: String): OverviewTag = new OverviewTagImpl(tag.copy(name = newName))
    override def withColor(newColor: String): OverviewTag with TagColor = new ColoredTag(tag, newColor)

    override def withColor: Option[OverviewTag with TagColor] = tag.color.map(new ColoredTag(tag, _))

    override def save = tag.save

    override def withDocumentInformation(implicit c: Connection): TaggedDocumentInformationImpl =
      new TaggedDocumentInformationImpl(tag)

  }

  private[models] class TaggedDocumentInformationImpl(tag: Tag, loader: PersistentTagLoader = new PersistentTagLoader, parser: DocumentListParser = new DocumentListParser)(implicit c: Connection) extends OverviewTagImpl(tag) with TaggedDocumentInformation {
    
    override val documentIds: models.core.DocumentIdList = {
      val documentListData = loader.loadDocumentList(id)
      parser.createDocumentIdList(documentListData)
    }
  }

  
}
