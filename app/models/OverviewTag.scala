/*
 * OverviewTag.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */
package models

import models.orm.Tag

/**
 * An interface for basic interactions with tags in the database.
 * When attributes are changed, a copy of the OverviewTag is returned
 * with the specified values changed. Changes must be explicitly saved
 * before being reflected in the database.
 */
trait OverviewTag {
  val id: Long
  val name: String

  /** @return a tag with the new name */
  def withName(newName: String): OverviewTag

  /** @return a tag with the specified color */
  def withColor(newColor: String): OverviewTag with TagColor

  /** @return an otptional tag if the color is set, None otherwise */
  def withColor: Option[OverviewTag with TagColor]
  
  /** save the tag with its attribute values */
  def save: OverviewTag

  /** 
   * delete the tag from the database. Any associations to other database elements
   * (such as tags on documents) are removed.
   */
  def delete: Unit
}

/** Trait providing color attribute */
trait TagColor {
  val color: String
}

/**
 * Provides a mechanism to find tags in a document set, or create new ones.
 */
case class PotentialTag(name: String) {
  // This class probably should be deleted, and OverviewTag called directly
  
  /** @return an optional tag if the tag exists in the documentSet, None otherwise. */
  def inDocumentSet(documentSetId: Long): Option[OverviewTag] = {
    OverviewTag.findByName(documentSetId, name)
  }

  /**
   * Creates a new tag in the documentSet. If a tag exists with the specified name
   * an exception will be thrown
   */
  def create(documentSetId: Long): OverviewTag = OverviewTag.create(documentSetId, name)

}

/**
 * Helper for finding and creating tags
 */
object OverviewTag {

  /** @return optional tag if tagName exists in the documentSet, None otherwise */
  def findByName(documentSetId: Long, tagName: String): Option[OverviewTag] =
    Tag.findByName(documentSetId, tagName).map(new OverviewTagImpl(_))

  /** @return a new tag with tagName in the documentSet */
  def create(documentSetId: Long, tagName: String): OverviewTag = {
    val newTag = Tag(documentSetId = documentSetId, name = tagName).save

    new OverviewTagImpl(newTag)

  }

  
  private class OverviewTagImpl(tag: Tag) extends OverviewTag {
    private class ColoredTag(source: Tag, override val color: String) extends OverviewTagImpl(source.copy(color = Some(color))) with TagColor

    override val id: Long = tag.id
    override val name: String = tag.name

    override def withName(newName: String): OverviewTag = new OverviewTagImpl(tag.copy(name = newName))
    override def withColor(newColor: String): OverviewTag with TagColor = new ColoredTag(tag, newColor)

    override def withColor: Option[OverviewTag with TagColor] = tag.color.map(new ColoredTag(tag, _))

    override def save: OverviewTag = {
      tag.save
      this
    }
    
    override def delete = tag.delete
  }


  
}
