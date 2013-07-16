package views.json.DocumentSet

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.Play.{start, stop}
import play.api.libs.json.Json.toJson
import play.api.test.{FakeApplication,FakeRequest}

import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.test.Specification
import models.OverviewUser

class showSpec extends Specification {
  step(start(FakeApplication()))

  "DocumentSet view generated Json" should {
    trait DocumentSetContext extends Scope with Mockito {
      implicit val request = FakeRequest()
      val documentSet = DocumentSet()
      val user = mock[OverviewUser].smart
      user.isAdministrator returns false
      
      lazy val documentSetJson = show(user, documentSet).toString
    }

    "contain id and html" in new DocumentSetContext {
      documentSetJson must /("id" -> documentSet.id)
      documentSetJson must beMatching(""".*"html":"[^<]*<.*>[^>]*".*""")
      documentSetJson must not contain ("state")
    }
  }

  step(stop)
}
