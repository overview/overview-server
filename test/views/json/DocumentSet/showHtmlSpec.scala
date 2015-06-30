package views.json.DocumentSet

import models.User

class showHtmlSpec extends views.ViewSpecification {
  "DocumentSet view generated Json" should {
    "contain id and html" in new JsonViewSpecificationScope {
      val documentSet = factory.documentSet()
      override def result = showHtml(User(), documentSet, 1)
      json must /("id" -> documentSet.id)
      json must beMatching(""".*"html":"[^<]*<.*>[^>]*".*""")
      json must not contain ("state")
    }
  }
}
