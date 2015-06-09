package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.database.DatabaseProvider
import org.overviewproject.models.tables.DocumentProcessingErrors
import org.overviewproject.test.DbSpecification

class WriteDocumentProcessingErrorSpec extends DbSpecification {

  "WriteDocumentProcessingError" should {

    "write a DocumentProcessingError with name and message" in new DocumentSetContext {
      await(errorWriter.write(documentSet.id, filename, message))
      
      val savedValues = blockingDatabase.option(DocumentProcessingErrors)
        .map(d => (d.documentSetId, d.textUrl, d.message))
      savedValues must beSome(documentSet.id, filename, message)
    }
  }

  trait DocumentSetContext extends DbScope {
    val filename = "file"
    val message = "failure"

    val documentSet = factory.documentSet()

    val errorWriter = new TestWriteDocumentProcessingError

    class TestWriteDocumentProcessingError
      extends WriteDocumentProcessingError with DatabaseProvider

  }
}
