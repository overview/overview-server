package org.overviewproject.jobhandler.filegroup

import akka.actor.Actor
import akka.actor.Props
import akka.pattern.pipe
import org.overviewproject.jobhandler.filegroup.task.step.DocumentIdRequest
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol.RequestResponse
import scala.concurrent.Future
import org.overviewproject.database.SlickSessionProvider
import org.overviewproject.models.tables.Documents
import org.overviewproject.database.Slick.simple._
import org.overviewproject.database.SlickClient
import scala.concurrent.Await
import scala.concurrent.blocking
import scala.concurrent.duration.Duration

trait DocumentIdSupplier extends Actor {
  import context.dispatcher

  protected val documentSetId: Long

  protected var lastId: Option[Long] = None

  def receive = {

    case DocumentIdRequest(requestId, numberOfIds) => {
      val documentIds = generateIds(getLastId, numberOfIds)
      lastId = documentIds.lastOption
      sender ! RequestResponse(requestId, documentIds)
    }
    
  }
  
  // Needs to read db synchronously because we don't want to risk
  // having to parallel requests reading the same id twice
  // If the documentSetId does not exist in the db, return 0.
  protected def findMaxDocumentId: Long

  private def getLastId: Long =
    lastId.getOrElse {
      blocking {
        findMaxDocumentId
      }
    }
  
  private def generateIds(lastId: Long, numberOfIds: Int): Seq[Long] =
    Seq.tabulate(numberOfIds)(_ + lastId + 1)
}

object DocumentIdSupplier {
  def apply(documentSetId: Long) = Props(new DocumentIdSupplierImpl(documentSetId))

  private class DocumentIdSupplierImpl(
    override protected val documentSetId: Long) extends DocumentIdSupplier with SlickSessionProvider {
    import context.dispatcher

    override protected def findMaxDocumentId: Long = blockingDb { implicit session =>
      Documents
        .filter(_.documentSetId === documentSetId)
        .map(_.id).max
        .run.getOrElse(0l)
    }
  }
}