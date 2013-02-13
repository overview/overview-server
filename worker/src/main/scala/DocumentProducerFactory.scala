/*
 * DocumentProducerFactory.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package overview.util

import org.overviewproject.clustering.DocumentCloudSource
import org.overviewproject.http.{ AsyncHttpRetriever, DocumentCloudDocumentProducer }
import overview.util.Progress._
import org.overviewproject.csv.CsvImportDocumentProducer
import persistence.DocumentSet
import persistence.PersistentDocumentSetCreationJob

/** Common functionality for DocumentProducers */
trait DocumentProducer {
  /**
   * Produce the documents. There should probably be some restrictions
   * here to indicate that we're producing documents and feeding them
   * to DocumentConsumers.
   */
  def produce()
}

/** A consumer of documents */
trait DocumentConsumer {
  /** How the document text is received, along with a document id */
  def processDocument(documentId: Long, text: String)

  /** Called on the consumer when no more documents will be generated */
  def productionComplete()
}

/**
 * Factory for generating a DocumentProducer based on the documentSet.
 * Depending on the documentSet type either a DocumentCloudDocumentProducer
 * or a CsvImportDocumentProducer is generated.
 */
object DocumentProducerFactory {
  /** The maximum number of documents processed for a document set */
  private val MaxDocuments = 10000
  
  /** Return a DocumentProducer based on the DocumentSet type */
  def create(documentSetCreationJob: PersistentDocumentSetCreationJob, documentSet: DocumentSet, consumer: DocumentConsumer,
    progAbort: ProgressAbortFn, asyncHttpRetriever: AsyncHttpRetriever): DocumentProducer = documentSet.documentSetType match {
    case "DocumentCloudDocumentSet" =>
      val dcSource = new DocumentCloudSource(asyncHttpRetriever, MaxDocuments,
        documentSet.query.get, documentSetCreationJob.documentCloudUsername, documentSetCreationJob.documentCloudPassword)
      new DocumentCloudDocumentProducer(documentSetCreationJob.documentSetId, dcSource, consumer, progAbort)
    case "CsvImportDocumentSet" =>
      new CsvImportDocumentProducer(documentSetCreationJob.documentSetId, documentSetCreationJob.contentsOid.get, documentSet.uploadedFileId.get, consumer, MaxDocuments, progAbort)
  }
}