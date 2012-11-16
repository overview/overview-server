package overview.util

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito

import overview.http.{ AsyncHttpRetriever, DocumentCloudDocumentProducer }

import persistence.{ DocumentSet, PersistentDocumentSetCreationJob }

class DocumentProducerFactorySpec extends Specification with Mockito {


  "DocumentProducerFactory" should {
    
    "create a DocumentCloudDocumentProducer" in {
      val consumer = mock[DocumentConsumer]
      val documentSetCreationJob = mock[PersistentDocumentSetCreationJob]
      documentSetCreationJob.documentCloudUsername returns None
      documentSetCreationJob.documentCloudPassword returns None
      val asyncHttpRetriever = mock[AsyncHttpRetriever]
      
      val documentSet = DocumentSet("DocumentCloudDocumentSet", "title", Some("query"))
      val producer: DocumentProducer = DocumentProducerFactory.create(documentSetCreationJob, documentSet, consumer, {_ => true }, asyncHttpRetriever)
      
      producer match {
        case p: DocumentCloudDocumentProducer => success
        case _ => failure
      }
      
    }
  }
}