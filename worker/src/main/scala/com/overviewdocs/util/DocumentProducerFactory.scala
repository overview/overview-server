/*
 * DocumentProducerFactory.scala
 *
 * Overview
 * Created by Jonas Karlsson, November 2012
 */
package com.overviewdocs.util

import com.overviewdocs.http.{Credentials, DocumentCloudDocumentProducer}
import com.overviewdocs.models.{DocumentSet,DocumentSetCreationJob,DocumentSetCreationJobType}
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

/**
 * Factory for generating a DocumentProducer based on the documentSet.
 * Depending on the documentSet type either a DocumentCloudDocumentProducer
 * or a CsvImportDocumentProducer is generated.
 */
object DocumentProducerFactory {
  /** The maximum number of documents processed for a document set */
  private val MaxDocuments = Configuration.getInt("max_documents")

  /** Return a DocumentProducer based on the DocumentSet type */
  def create(documentSetCreationJob: DocumentSetCreationJob, documentSet: DocumentSet, progAbort: ProgressAbortFn): DocumentProducer = {

    documentSetCreationJob.jobType match {
      case DocumentSetCreationJobType.DocumentCloud =>
        val credentials = for {
          username <- documentSetCreationJob.documentcloudUsername
          password <- documentSetCreationJob.documentcloudPassword
        } yield Credentials(username, password)

        new DocumentCloudDocumentProducer(documentSetCreationJob, documentSet.query.get, credentials, MaxDocuments, progAbort)
    }
  }
}
