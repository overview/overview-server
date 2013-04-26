package org.overviewproject.persistence

import org.overviewproject.tree.orm.Document
import org.overviewproject.persistence.orm.{ Schema, Tag }
import org.overviewproject.tree.orm.DocumentTag

class DocumentTagWriter(documentSetId: Long) {

  def write(document: Document, tags: Iterable[Tag]): Unit = {
    val documentTags = tags.map(t => DocumentTag(document.id, t.id))
    
    Schema.documentTags.insert(documentTags)
  }
  
}