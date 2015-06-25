package org.overviewproject.util

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope

import org.overviewproject.http.DocumentCloudDocumentProducer
import org.overviewproject.persistence.PersistentDocumentSetCreationJob
import org.overviewproject.csv.CsvImportDocumentProducer
import org.overviewproject.tree.DocumentSetCreationJobType

class DocumentProducerFactorySpec extends Specification with Mockito {
  "DocumentProducerFactory" should {
    trait BaseScope extends Scope {
      val factory = org.overviewproject.test.factories.PodoFactory

      val consumer = smartMock[DocumentConsumer]
      val documentSetCreationJob = smartMock[PersistentDocumentSetCreationJob]
      documentSetCreationJob.documentCloudUsername returns None
      documentSetCreationJob.documentCloudPassword returns None
    }

    "create a DocumentCloudDocumentProducer" in new BaseScope {
      documentSetCreationJob.jobType returns DocumentSetCreationJobType.DocumentCloud
      documentSetCreationJob.contentsOid returns None
      val documentSet = factory.documentSet(title="title", query=Some("query"))
      val producer: DocumentProducer = DocumentProducerFactory.create(documentSetCreationJob, documentSet, consumer, { _ => true })

      producer match {
        case p: DocumentCloudDocumentProducer => success
        case _ => failure
      }
    }

    "create a CsvImportDocumentProducer" in new BaseScope {
      documentSetCreationJob.jobType returns DocumentSetCreationJobType.CsvUpload
      documentSetCreationJob.contentsOid returns Some(0l)
      val documentSet = factory.documentSet(title="title", uploadedFileId = Some(100L))
      val producer: DocumentProducer = DocumentProducerFactory.create(documentSetCreationJob, documentSet, consumer, {_ => true })

      producer match {
        case p: CsvImportDocumentProducer => success
        case _ => failure
      }
    }
  }
}
