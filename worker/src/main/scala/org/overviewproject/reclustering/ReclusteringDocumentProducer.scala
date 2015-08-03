package org.overviewproject.reclustering

import scala.concurrent.{ExecutionContext,Future}
import slick.backend.DatabasePublisher

import org.overviewproject.database.HasBlockingDatabase
import org.overviewproject.models.Document
import org.overviewproject.models.tables.{DocumentTags,Documents}
import org.overviewproject.util.DocumentConsumer
import org.overviewproject.util.DocumentProducer
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Retrieving
import org.overviewproject.util.Progress._

trait ReclusteringDocumentProducer extends DocumentProducer {
  protected class AbortedException extends Exception

  protected val FetchingFraction: Double = 0.5
  protected val consumer: DocumentConsumer
  protected val progAbort: ProgressAbortFn
  protected val nDocuments: Int
  protected val documentStream: DatabasePublisher[(Long,String)]
  protected val progressReportThrottle: Long = 500 // ms between progress reports (which are also abort checks)
  protected implicit val ec: ExecutionContext

  override def produce(): Int = {
    scala.concurrent.Await.result(produceAsync, scala.concurrent.duration.Duration.Inf)
  }

  private def produceAsync: Future[Int] = {
    var lastProgressReportTime: Long = System.currentTimeMillis()
    var nProcessed = 0

    def handleDocument(id: Long, text: String): Unit = {
      consumer.processDocument(id, text)
      nProcessed += 1

      val time = System.currentTimeMillis()
      if (time - lastProgressReportTime > progressReportThrottle) {
        val abort = progAbort(Progress(
          FetchingFraction * nProcessed / nDocuments,
          Retrieving(nProcessed, nDocuments)
        ))
        if (abort) throw new AbortedException
        lastProgressReportTime = time
      }
    }

    documentStream                                        // Stream of (id,text)
      .foreach((handleDocument _).tupled)                 // Future[Unit], when done
      .map(_ => consumer.productionComplete)              // Future[Unit]
      .map(_ => nDocuments)                               // Future[Int]
      .recover { case e: AbortedException => nDocuments } // Future[Int]
  }
}

object ReclusteringDocumentProducer extends HasBlockingDatabase {
  private val PageSize = 100

  def streamDocuments(documentSetId: Long, maybeTagId: Option[Long]): (Int,DatabasePublisher[(Long,String)]) = {
    import database.api._

    val baseQuery = Documents
      .filter(_.documentSetId === documentSetId)
      .map(d => (d.id, d.text.getOrElse("")))

    val query = maybeTagId match {
      case None => baseQuery
      case Some(tagId) => {
        baseQuery.filter(_._1 in DocumentTags.filter(_.tagId === tagId).map(_.documentId))
      }
    }

    (blockingDatabase.length(query), database.slickDatabase.stream(query.result))
  }

  def apply(documentSetId: Long, maybeTagId: Option[Long], aConsumer: DocumentConsumer, aProgAbort: ProgressAbortFn): ReclusteringDocumentProducer = new ReclusteringDocumentProducer {
    override protected val consumer: DocumentConsumer = aConsumer
    override protected val progAbort: ProgressAbortFn = aProgAbort
    override protected val (nDocuments, documentStream) = streamDocuments(documentSetId, maybeTagId)
    override implicit val ec = database.executionContext
  }
}
