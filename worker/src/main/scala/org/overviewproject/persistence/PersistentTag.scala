package org.overviewproject.persistence

import org.overviewproject.persistence.orm.Tag
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.util.TagColorList

object PersistentTag {

  def findOrCreate(documentSetId: Long, tagName: String): Tag = {
    val foundTag = Schema.tags.where(t => t.documentSetId === documentSetId and t.name === tagName).headOption
    foundTag.getOrElse {
      val color = TagColorList.forString(tagName)
      val newTag = Tag(documentSetId=documentSetId, name=tagName, color=color)
      Schema.tags.insertOrUpdate(newTag)
    }
  }
}
