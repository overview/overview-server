package org.overviewproject.reclustering

import org.overviewproject.util.DocumentProducer
import org.overviewproject.util.DocumentConsumer
import org.overviewproject.util.Progress._
import org.overviewproject.persistence.orm.finders.DocumentFinder
import org.overviewproject.tree.orm.Document
import scala.annotation.tailrec
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Retrieving

trait ReclusteringDocumentProducer extends DocumentProducer {
  protected val FetchingFraction: Double = 0.5
  protected val consumer: DocumentConsumer
  protected val progAbort: ProgressAbortFn
  protected val pagedDocumentFinder: PagedDocumentFinder
  protected lazy val totalNumberOfDocuments = pagedDocumentFinder.numberOfDocuments

  override def produce(): Int = {
    produceDocuments(1, 0)
  }

  @tailrec
  private def produceDocuments(currentPage: Int, numberOfDocumentsProcessed: Int): Int = {
    val documents = pagedDocumentFinder.findDocuments(currentPage)

    if (documents.isEmpty) numberOfDocumentsProcessed
    else {
      val numberOfDocumentsInPage = processDocuments(documents)
      produceDocuments(currentPage + 1, numberOfDocumentsProcessed + numberOfDocumentsInPage)
    }
  }

  private def processDocuments(documents: Iterable[Document]): Int = {
    case class State(numberOfDocuments: Int, cancelled: Boolean)
    val result = documents.foldLeft(State(0, false)) { (s, document) =>
      if (!s.cancelled) document.text.map { text =>
        consumer.processDocument(document.id, text)
        State(s.numberOfDocuments + 1, reportProgress(s.numberOfDocuments + 1))
      }.getOrElse(State(s.numberOfDocuments, reportProgress(s.numberOfDocuments)))
      else s
    }

    result.numberOfDocuments
  }

  private def reportProgress(documentIndex: Int): Boolean = {
    val completionFraction = FetchingFraction * documentIndex / totalNumberOfDocuments
    val status = Retrieving(documentIndex, totalNumberOfDocuments.toInt)
    progAbort(Progress(completionFraction, status))
  }
}

object ReclusteringDocumentProducer {
  private val PageSize = 100
  def apply(documentSetId: Long, aConsumer: DocumentConsumer, aProgAbort: ProgressAbortFn): ReclusteringDocumentProducer =
    new ReclusteringDocumentProducer {
      override protected val consumer: DocumentConsumer = aConsumer
      override protected val progAbort: ProgressAbortFn = aProgAbort
      override protected val pagedDocumentFinder: PagedDocumentFinder = 
        PagedDocumentFinder(documentSetId, PageSize)

    }
}