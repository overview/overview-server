package org.overviewproject.reclustering

import org.overviewproject.util.DocumentProducer
import org.overviewproject.util.DocumentConsumer
import org.overviewproject.util.Progress._
import org.overviewproject.persistence.orm.finders.DocumentFinder
import org.overviewproject.tree.orm.Document
import scala.annotation.tailrec
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Retrieving
import org.overviewproject.util.Logger

trait ReclusteringDocumentProducer extends DocumentProducer {
  protected val FetchingFraction: Double = 0.5
  protected val consumer: DocumentConsumer
  protected val progAbort: ProgressAbortFn
  protected val pagedDocumentFinder: PagedDocumentFinder
  protected lazy val totalNumberOfDocuments = pagedDocumentFinder.numberOfDocuments

  private case class ProductionState(numberOfDocuments: Int, cancelled: Boolean)

  override def produce(): Int = {
    val numberOfDocumentsProduced = produceDocuments(1, 0)
    consumer.productionComplete()

    numberOfDocumentsProduced
  }

  @tailrec
  private def produceDocuments(currentPage: Int, numberOfDocumentsProcessed: Int): Int = {
    val documents = pagedDocumentFinder.findDocuments(currentPage)

    if (documents.isEmpty) numberOfDocumentsProcessed
    else {
      val state = processDocuments(documents, numberOfDocumentsProcessed)
      if (state.cancelled) state.numberOfDocuments
      else produceDocuments(currentPage + 1, state.numberOfDocuments)
    }
  }

  private def processDocuments(documents: Iterable[Document], numberOfDocumentsProcessed: Int): ProductionState = {
    documents.foldLeft(ProductionState(numberOfDocumentsProcessed, false)) { (s, document) =>
      if (!s.cancelled) document.text.map { text =>
        consumer.processDocument(document.id, text)
        ProductionState(s.numberOfDocuments + 1, reportProgress(s.numberOfDocuments + 1))
      }.getOrElse(ProductionState(s.numberOfDocuments, reportProgress(s.numberOfDocuments)))
      else s
    }
  }

  private def reportProgress(documentIndex: Int): Boolean = {
    val completionFraction = FetchingFraction * documentIndex / totalNumberOfDocuments
    val status = Retrieving(documentIndex, totalNumberOfDocuments.toInt)
    progAbort(Progress(completionFraction, status))
  }
}

object ReclusteringDocumentProducer {
  private val PageSize = 100
  def apply(documentSetId: Long, tagId: Option[Long], aConsumer: DocumentConsumer, aProgAbort: ProgressAbortFn): ReclusteringDocumentProducer =
    new ReclusteringDocumentProducer {
      override protected val consumer: DocumentConsumer = aConsumer
      override protected val progAbort: ProgressAbortFn = aProgAbort
      override protected val pagedDocumentFinder: PagedDocumentFinder =
        PagedDocumentFinder(documentSetId, tagId, PageSize)

    }
}