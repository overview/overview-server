package overview.util

import overview.clustering.{ DCDocumentAtURL, DocumentCloudSource, DocumentSetIndexer }
import overview.http.{AsyncHttpRetriever, DocumentCloudDocumentProducer}
import overview.util.Progress._
import persistence.DocumentSet
import persistence.PersistentDocumentSetCreationJob

trait DocumentProducer {

}

trait DocumentConsumer {
  def processDocument(documentSetId: Long, text: String)
  def productionComplete()
}

object DocumentProducerFactory {
  def create(documentSetCreationJob: PersistentDocumentSetCreationJob, documentSet: DocumentSet, consumer: DocumentConsumer, 
      progAbort: ProgressAbortFn, asyncHttpRetriever: AsyncHttpRetriever): DocumentProducer = {
    val dcSource = new DocumentCloudSource(asyncHttpRetriever,
      documentSet.query.get, documentSetCreationJob.documentCloudUsername, documentSetCreationJob.documentCloudPassword)
    new DocumentCloudDocumentProducer(documentSetCreationJob.documentSetId, dcSource, consumer, progAbort)
  }
}