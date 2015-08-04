/*
 * DocumentProducerFactory.scala
 *
 * Overview
 * Created by Jonas Karlsson, November 2012
 */
package com.overviewdocs.util

import com.overviewdocs.csv.CsvImportDocumentProducer
import com.overviewdocs.http.{Credentials, DocumentCloudDocumentProducer}
import com.overviewdocs.models.DocumentSet
import com.overviewdocs.tree.DocumentSetCreationJobType
import com.overviewdocs.persistence.PersistentDocumentSetCreationJob
import com.overviewdocs.reclustering.ReclusteringDocumentProducer
import com.overviewdocs.util.Progress.ProgressAbortFn

/** Common functionality for DocumentProducers */
trait DocumentProducer {
  /**
   * Produce the documents. There should probably be some restrictions
   * here to indicate that we're producing documents and feeding them
   * to DocumentConsumers.
   *
   * @return the number of documents produced.
   */
  def produce(): Int
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
  private val MaxDocuments = Configuration.getInt("max_documents")

  /** Return a DocumentProducer based on the DocumentSet type */
  def create(documentSetCreationJob: PersistentDocumentSetCreationJob, documentSet: DocumentSet, consumer: DocumentConsumer,
    progAbort: ProgressAbortFn): DocumentProducer = {

    documentSetCreationJob.jobType match {
      case DocumentSetCreationJobType.DocumentCloud =>
        val credentials = for {
          username <- documentSetCreationJob.documentCloudUsername
          password <- documentSetCreationJob.documentCloudPassword
        } yield Credentials(username, password)

        new DocumentCloudDocumentProducer(documentSetCreationJob, documentSet.query.get, credentials, MaxDocuments, progAbort)
      case DocumentSetCreationJobType.CsvUpload =>
        new CsvImportDocumentProducer(documentSetCreationJob.documentSetId, documentSetCreationJob.contentsOid.get, documentSet.uploadedFileId.get, MaxDocuments, progAbort)
      case DocumentSetCreationJobType.Recluster =>
        ReclusteringDocumentProducer(documentSetCreationJob.documentSetId, documentSetCreationJob.tagId, consumer, progAbort)
    }
  }
}
