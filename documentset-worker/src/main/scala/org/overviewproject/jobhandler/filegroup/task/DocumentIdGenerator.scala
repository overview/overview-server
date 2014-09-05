package org.overviewproject.jobhandler.filegroup.task

import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.DocumentFinder
import org.overviewproject.database.orm.finders.FinderById

/**
 * Generates ids for documents in document sets.
 * The high bytes are the document set id, and the low bytes are an index.
 * If the document set already exists, the index starts at `documentSet.documentCount + 1`.
 * This scheme will fail if the document ids are not contiguous. Getting the max id is too slow, however.
 * 
 */
trait DocumentIdGenerator {

  val documentSetId: Long

  def nextId: Long = {
    lastUsedId += 1
    (documentSetId << 32) | lastUsedId
  }

  protected def existingDocumentCount: Long

  private var lastUsedId = existingDocumentCount
}


/** Factory for [[DocumentIdGenerator]] */
object DocumentIdGenerator {

  def apply(documentSetId: Long): DocumentIdGenerator = {
    new DocumentIdGeneratorImpl(documentSetId)
  }

  private class DocumentIdGeneratorImpl(override val documentSetId: Long) extends DocumentIdGenerator {
    override protected def existingDocumentCount: Long = Database.inTransaction {
      import org.overviewproject.database.orm.Schema.documentSets
      
      val documentSetFinder = new FinderById(documentSets)
      
      documentSetFinder.byId(documentSetId).headOption.map { _.documentCount.toLong }.getOrElse(0)
      
    }
  }
}