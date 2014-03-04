package org.overviewproject.fileupload

import org.overviewproject.util.DocumentProducer
import org.overviewproject.util.DocumentConsumer
import org.overviewproject.util.Progress._
import org.overviewproject.tree.orm.Document
import scala.annotation.tailrec
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Retrieving


trait PagedDocumentSourceDocumentProducer[T] extends DocumentProducer {
  protected val PreparingFraction: Double
  protected val FetchingFraction: Double
  protected val consumer: DocumentConsumer
  protected val progAbort: ProgressAbortFn
  protected val totalNumberOfDocuments: Long

  protected def processDocumentSource(documentSource: T): Unit
  protected def runQueryForPage(pageNumber: Int)(processDocumentSources: Iterable[T] => Int): Int

  override def produce(): Int = {
    val numberOfDocumentsProduced = produceDocumentsByPage
    consumer.productionComplete()

    numberOfDocumentsProduced
  }

  private def produceDocumentsByPage: Int = produceDocumentsFromPage(1, 0)

  @tailrec
  private def produceDocumentsFromPage(currentPage: Int, numberOfDocumentsProcessed: Int): Int = {

    val numberOfDocumentsProcessedInPage = runQueryForPage(currentPage) { sources =>
      sources.foldLeft(0) { (numberOfDocuments, source) =>
        val cancel = reportProgress(numberOfDocumentsProcessed + numberOfDocuments + 1)
        if (!cancel) {
          processDocumentSource(source)
          numberOfDocuments + 1
        } else numberOfDocuments
      }
    }


    if (numberOfDocumentsProcessedInPage == 0) numberOfDocumentsProcessed
    else produceDocumentsFromPage(currentPage + 1, numberOfDocumentsProcessedInPage + numberOfDocumentsProcessed)
  }

  private def reportProgress(documentIndex: Int): Boolean = {
    val completionFraction = PreparingFraction + FetchingFraction * documentIndex / totalNumberOfDocuments
    val status = Retrieving(documentIndex, totalNumberOfDocuments.toInt)
    progAbort(Progress(completionFraction, status))
  }

}