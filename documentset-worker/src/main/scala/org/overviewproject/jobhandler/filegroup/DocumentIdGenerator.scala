package org.overviewproject.jobhandler.filegroup

import scala.concurrent.ExecutionContext.Implicits.global

import org.overviewproject.database.{HasBlockingDatabase,BlockingDatabaseProvider}
import org.overviewproject.models.tables.Documents

/**
 * Used by [[DocumentIdSupplier]] to generate ids for [[Document]s
 */
trait DocumentIdGenerator extends HasBlockingDatabase {
  protected val documentSetId: Long
  private var lastId: Option[Long] = None

  def nextIds(numberOfIds: Int): Seq[Long] = {
    val ids = generateIds(getLastId, numberOfIds)

    lastId = ids.lastOption

    ids
  }

  private def generateIds(lastId: Long, numberOfIds: Int): Seq[Long] = {
    Seq.tabulate(numberOfIds)(_ + lastId + 1)
  }

  private def getLastId: Long = lastId.getOrElse(findMaxDocumentId)

  // Needs to read db synchronously because we don't want to risk
  // having to parallel requests reading the same id twice
  // If the documentSetId does not exist in the db, return 0.
  //
  // The assumption, by the way, is that there is only one DocumentIdGenerator
  // anywhere.
  private def findMaxDocumentId: Long = blockingDatabase.run {
    import blockingDatabaseApi._
    Documents
      .filter(_.documentSetId === documentSetId)
      .map(_.id).max
      .result // Option[Long]
      .map(_.getOrElse(documentSetId << 32))
  }
}

object DocumentIdGenerator {
  def apply(documentSetId: Long): DocumentIdGenerator = new DocumentIdGeneratorImpl(documentSetId)

  private class DocumentIdGeneratorImpl(override protected val documentSetId: Long)
    extends DocumentIdGenerator with BlockingDatabaseProvider
}
