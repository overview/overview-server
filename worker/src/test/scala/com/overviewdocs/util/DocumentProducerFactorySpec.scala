package com.overviewdocs.util

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope

import com.overviewdocs.http.DocumentCloudDocumentProducer
import com.overviewdocs.models.{DocumentSetCreationJob,DocumentSetCreationJobType}

class DocumentProducerFactorySpec extends Specification with Mockito {
  "DocumentProducerFactory" should {
    trait BaseScope extends Scope {
      val factory = com.overviewdocs.test.factories.PodoFactory

      val documentSetCreationJob = smartMock[DocumentSetCreationJob]
      documentSetCreationJob.documentcloudUsername returns None
      documentSetCreationJob.documentcloudPassword returns None
    }

    "create a DocumentCloudDocumentProducer" in new BaseScope {
      documentSetCreationJob.jobType returns DocumentSetCreationJobType.DocumentCloud
      val documentSet = factory.documentSet(title="title", query=Some("query"))
      val producer: DocumentProducer = DocumentProducerFactory.create(documentSetCreationJob, documentSet, { _ => true })

      producer match {
        case p: DocumentCloudDocumentProducer => success
        case _ => failure
      }
    }
  }
}
