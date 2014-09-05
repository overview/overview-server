package org.overviewproject.jobhandler.filegroup.task

import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.DocumentFinder
import org.overviewproject.database.orm.finders.FinderById

trait DocumentIdGenerator {

  val documentSetId: Long

  def nextId: Long = {
    lastUsedId += 1
    (documentSetId << 32) | lastUsedId
  }

  protected def largestExistingId: Long

  private var lastUsedId = largestExistingId
}

object DocumentIdGenerator {

  def apply(documentSetId: Long): DocumentIdGenerator = {
    new DocumentIdGeneratorImpl(documentSetId)
  }

  private class DocumentIdGeneratorImpl(override val documentSetId: Long) extends DocumentIdGenerator {
    override protected def largestExistingId: Long = Database.inTransaction {
      import org.overviewproject.database.orm.Schema.documentSets
      
      val documentSetFinder = new FinderById(documentSets)
      
      documentSetFinder.byId(documentSetId).headOption.map { _.documentCount.toLong }.getOrElse(0)
      
    }
  }
}