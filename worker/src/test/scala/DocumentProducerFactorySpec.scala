package overview.util

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import overview.http.{ AsyncHttpRetriever, DocumentCloudDocumentProducer }
import persistence.{ DocumentSet, PersistentDocumentSetCreationJob }
import org.specs2.mutable.Before
import csv.CsvImportDocumentProducer

class DocumentProducerFactorySpec extends Specification with Mockito {

  "DocumentProducerFactory" should {

    trait DocumentSetCreationJobContext extends Before {
      val consumer = mock[DocumentConsumer]
      val documentSetCreationJob = mock[PersistentDocumentSetCreationJob]
      val asyncHttpRetriever = mock[AsyncHttpRetriever]

      def before() = {
        documentSetCreationJob.documentCloudUsername returns None
        documentSetCreationJob.documentCloudPassword returns None
      }
    }

    "create a DocumentCloudDocumentProducer" in new DocumentSetCreationJobContext {

      val documentSet = DocumentSet("DocumentCloudDocumentSet", "title", Some("query"))
      val producer: DocumentProducer = DocumentProducerFactory.create(documentSetCreationJob, documentSet, consumer, { _ => true }, asyncHttpRetriever)

      producer match {
        case p: DocumentCloudDocumentProducer => success
        case _ => failure
      }
    }

    "create a CsvImportDocumentProducer" in new DocumentSetCreationJobContext {
      val documentSet = DocumentSet("CsvImportDocumentSet", "title", uploadedFileId = Some(100l))
      val producer: DocumentProducer = DocumentProducerFactory.create(documentSetCreationJob, documentSet, consumer, {_ => true }, asyncHttpRetriever)

      producer match {
        case p: CsvImportDocumentProducer => success
        case _ => failure
      }
    }
  }
}