package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.database.Slick.simple._
import slick.jdbc.JdbcBackend.Session
import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession
import org.overviewproject.models.tables.DocumentProcessingErrors

class WriteDocumentProcessingErrorSpec extends SlickSpecification {

  "WriteDocumentProcessingError" should {

    "write a DocumentProcessingError with name and message" in new DocumentSetContext {
      await(errorWriter.write(documentSet.id, filename, message))
      
      val savedValues = DocumentProcessingErrors.map(d => (d.documentSetId, d.textUrl, d.message)).firstOption
      savedValues must beSome(documentSet.id, filename, message)
    }
  }

  trait DocumentSetContext extends DbScope {
    val filename = "file"
    val message = "failure"

    val documentSet = factory.documentSet()

    val errorWriter = new TestWriteDocumentProcessingError

    class TestWriteDocumentProcessingError(implicit val session: Session)
      extends WriteDocumentProcessingError with SlickClientInSession

  }
}