package org.overviewproject.persistence

import org.overviewproject.tree.orm.Document
import org.overviewproject.persistence.orm.{ Schema, Tag }
import org.overviewproject.tree.orm.DocumentTag

class DocumentTagWriter(documentSetId: Long) {
   val BatchSize: Int = 500
   val batchInserter = new BatchInserter(BatchSize, Schema.documentTags)
   
  def write(document: Document, tags: Iterable[Tag]): Unit = {
    val documentTags = tags.map(t => DocumentTag(document.id, t.id))
    
    documentTags.map(batchInserter.insert)
    
  }
  
  def flush(): Unit = batchInserter.flush
  
  
}