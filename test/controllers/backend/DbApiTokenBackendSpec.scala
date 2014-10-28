package controllers.backend

import play.api.libs.json.Json

import org.overviewproject.models.ApiToken
import org.overviewproject.models.tables.ApiTokens

class DbApiTokenBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbApiTokenBackend

    def findApiToken(token: String) = {
      import org.overviewproject.database.Slick.simple._
      ApiTokens.filter(_.token === token).firstOption(session)
    }
  }

  "DbApiTokenBackend" should {
    "#create" should {
      trait CreateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val documentSetId = documentSet.id
        val attributes = ApiToken.CreateAttributes(
          email="user@example.org",
          description="description"
        )

        def createApiToken = await(backend.create(documentSetId, attributes))
        lazy val apiToken = createApiToken
      }

      "return an ApiToken" in new CreateScope {
        apiToken.documentSetId must beEqualTo(documentSet.id)
        apiToken.createdBy must beEqualTo("user@example.org")
        apiToken.description must beEqualTo("description")
      }

      "write the ApiToken to the database" in new CreateScope {
        val dbApiToken = findApiToken(apiToken.token)
        dbApiToken must beSome(apiToken)
      }

      "pick unique tokens" in new CreateScope {
        val ret1 = createApiToken
        val ret2 = createApiToken
        ret1.token must not(beEqualTo(ret2.token))
      }

      "fail when writing an invalid document set ID" in new CreateScope {
        override val documentSetId = documentSet.id + 1L
        createApiToken must throwA[exceptions.ParentMissing]
      }
    }

    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=documentSet.id)
        def destroy = await(backend.destroy(apiToken.token))
      }

      "delete the ApiToken" in new DestroyScope {
        destroy
        findApiToken(apiToken.token) must beNone
      }

      "not delete a different ApiToken" in new DestroyScope {
        val apiToken2 = factory.apiToken(documentSetId=documentSet.id, token="token2")
        destroy
        findApiToken(apiToken2.token) must beSome
      }

      "throw ParentMissing if there is a Store with this ApiToken" in new DestroyScope {
        val store = factory.store(apiToken=apiToken.token)
        destroy must throwA[exceptions.ParentMissing]
      }

      "throw ParentMissing if there is a View with this ApiToken" in new DestroyScope {
        val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
        destroy must throwA[exceptions.ParentMissing]
      }
    }
  }
}
