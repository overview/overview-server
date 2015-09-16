package com.overviewdocs.persistence

import com.overviewdocs.documentcloud.DocumentRetrievalError
import com.overviewdocs.models.tables.{DocumentProcessingErrors,DocumentSets}
import com.overviewdocs.test.DbSpecification

class DocRetrievalErrorWriterSpec extends DbSpecification {
  trait OurContext extends DbScope {
    val documentSet = factory.documentSet()
    val errors = Seq.tabulate(3)(i => DocumentRetrievalError("url" + i, "error: " + i, Some(i), Some(Map("foo" -> Seq("bar")))))

    DocRetrievalErrorWriter.write(documentSet.id, errors)
  }
  
  "write out error data" in new OurContext {
    import database.api._

    val foundErrors = blockingDatabase.seq(DocumentProcessingErrors.filter(_.documentSetId === documentSet.id))

    foundErrors.length must beEqualTo(3)
    foundErrors.head.headers must beSome("foo: bar")
  }

  "write document_set.document_processing_error_count" in new OurContext {
    import database.api._

    blockingDatabase.option(DocumentSets.filter(_.id === documentSet.id))
      .map(_.documentProcessingErrorCount) must beSome(3)
  }
}
