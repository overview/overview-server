package views.json.DocumentSet

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.Json.toJson

import org.overviewproject.test.Specification
import models.OverviewDocumentSet

class showSpec extends Specification {

  "DocumentSet view generated Json" should {
    trait DocumentSetContext extends Scope with Mockito {
      val documentSet = mock[OverviewDocumentSet]
      lazy val documentSetJson = show(documentSet).toString
    }

    "contain id and html" in new DocumentSetContext {
      documentSetJson must /("id" -> documentSet.id)
      documentSetJson must beMatching(""".*"html":"[^<]*<.*>[^>]*".*""")
      documentSetJson must not contain ("state")
    }
  }
}
