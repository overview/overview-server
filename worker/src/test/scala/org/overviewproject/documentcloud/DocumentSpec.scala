package org.overviewproject.documentcloud

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class RetrievableDocumentSpec extends Specification {

  "RetrievableDocument" should {
    
    trait DocumentContext extends Scope {
      val id = "documentCloudId"
      val title = "title"
      val pageUrlPrefix = "pageUrlTemplate-p"
      val document = Document(id, title, "access", s"$pageUrlPrefix{page}")
    }

    "return the document text url for CompleteDocuments" in new DocumentContext {
      document.url must be equalTo(s"https://www.documentcloud.org/api/documents/$id.txt")
    }

  }
}