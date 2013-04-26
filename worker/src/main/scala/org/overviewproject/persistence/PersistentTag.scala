package org.overviewproject.persistence

import org.overviewproject.persistence.orm.Tag
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._

object PersistentTag {

  def findOrCreate(documentSetId: Long, tagName: String): Tag = {
    val foundTag = Schema.tags.where(t => t.documentSetId === documentSetId and t.name === tagName).headOption
    foundTag.getOrElse {
      val newTag = Tag(documentSetId, tagName)
      Schema.tags.insertOrUpdate(newTag)
    }
  }
}