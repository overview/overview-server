package org.overviewproject.jobhandler.filegroup

import scala.concurrent.blocking
import org.overviewproject.database.SlickClient
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables.Documents
import org.overviewproject.database.SlickSessionProvider

/**
 * Used by [[DocumentIdSupplier]] to generate ids for [[Document]s
 */
trait DocumentIdGenerator extends SlickClient {

  protected val documentSetId: Long

  def nextIds(numberOfIds: Int): Seq[Long] = {

    val ids = generateIds(getLastId, numberOfIds)

    lastId = ids.lastOption

    ids
  }

  private var lastId: Option[Long] = None

  private def generateIds(lastId: Long, numberOfIds: Int): Seq[Long] =
    Seq.tabulate(numberOfIds)(_ + lastId + 1)

  private def getLastId: Long =
    lastId.getOrElse {
      blocking {
        findMaxDocumentId
      }
    }

  // Needs to read db synchronously because we don't want to risk
  // having to parallel requests reading the same id twice
  // If the documentSetId does not exist in the db, return 0.
  private def findMaxDocumentId: Long = blockingDb { implicit session =>
    Documents
      .filter(_.documentSetId === documentSetId)
      .map(_.id).max
      .run.getOrElse(documentSetId << 32)
  }

}

object DocumentIdGenerator {

  def apply(documentSetId: Long): DocumentIdGenerator = new DocumentIdGeneratorImpl(documentSetId)

  private class DocumentIdGeneratorImpl(
    override protected val documentSetId: Long) extends DocumentIdGenerator with SlickSessionProvider

}
