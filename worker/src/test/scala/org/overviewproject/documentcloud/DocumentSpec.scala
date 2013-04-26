package org.overviewproject.documentcloud

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DocumentSpec extends Specification {

  "Document" should {
    
    trait DocumentContext extends Scope {
      val id = "documentCloudId"
      val title = "title"
      val textUrl = "textUrl"
      val pageUrlPrefix = "pageUrlTemplate-p"
      val document = Document(id, title, 1, "access", textUrl, s"$pageUrlPrefix{page}")
    }

    "return the document text url for CompleteDocuments" in new DocumentContext {
      document.url must be equalTo(textUrl)
    }

  }
}