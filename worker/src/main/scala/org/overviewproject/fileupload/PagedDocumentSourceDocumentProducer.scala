package org.overviewproject.fileupload

import org.overviewproject.util.DocumentProducer
import org.overviewproject.util.DocumentConsumer
import org.overviewproject.util.Progress._
import org.overviewproject.tree.orm.Document
import scala.annotation.tailrec
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Retrieving

/**
 * `PagedDocumentProducer` enables document production by reading input data from the database without reading
 * all query results into memory at once. Override `runQueryForPage` to query one ResultPage and process 
 * the results.
 * The type T of the trait should be the type of the query result.
 * 
 */
trait PagedDocumentSourceDocumentProducer[T] extends DocumentProducer {
  protected val PreparingFraction: Double
  protected val FetchingFraction: Double
  protected val consumer: DocumentConsumer
  protected val progAbort: ProgressAbortFn
  protected val totalNumberOfDocuments: Long

  /**
   * Converts one result from the query into one or more document, calling `consumer.processDocument` for each one.
   * 
   * @return the number of documents produced.
   */
  protected def processDocumentSource(documentSource: T): Int
  
  
  /**
   * Execute a query, returning a `ResultPage` for the specified `pageNumber`. Call the given `processDocumentSources`
   * function on the results.
   * @return the return value of `processDocumentSources`
   */
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
          val numberOfNewDocuments =  processDocumentSource(source)
          numberOfDocuments + numberOfNewDocuments
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