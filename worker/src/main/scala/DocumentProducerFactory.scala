package overview.util

import overview.clustering.{ DCDocumentAtURL, DocumentCloudSource, DocumentSetIndexer }
import overview.http.{ AsyncHttpRetriever, DocumentCloudDocumentProducer }
import overview.util.Progress._
import persistence.DocumentSet
import persistence.PersistentDocumentSetCreationJob
import csv.CsvImportDocumentProducer

trait DocumentProducer {
  def produce()
}

trait DocumentConsumer {
  def processDocument(documentSetId: Long, text: String)
  def productionComplete()
}

object DocumentProducerFactory {
  def create(documentSetCreationJob: PersistentDocumentSetCreationJob, documentSet: DocumentSet, consumer: DocumentConsumer,
    progAbort: ProgressAbortFn, asyncHttpRetriever: AsyncHttpRetriever): DocumentProducer = documentSet.documentSetType match {
    case "DocumentCloudDocumentSet" =>
      val dcSource = new DocumentCloudSource(asyncHttpRetriever,
        documentSet.query.get, documentSetCreationJob.documentCloudUsername, documentSetCreationJob.documentCloudPassword)
      new DocumentCloudDocumentProducer(documentSetCreationJob.documentSetId, dcSource, consumer, progAbort)
   case "CsvImportDocumentSet" =>
     new CsvImportDocumentProducer(documentSetCreationJob.documentSetId, documentSet.uploadedFileId.get, consumer, progAbort)
  }
}