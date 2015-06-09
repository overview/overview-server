package controllers.backend

import play.api.libs.json.Json

import org.overviewproject.database.exceptions
import org.overviewproject.models.ApiToken
import org.overviewproject.models.tables.ApiTokens

class DbApiTokenBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbApiTokenBackend with org.overviewproject.database.DatabaseProvider

    def findApiToken(token: String) = {
      import databaseApi._
      blockingDatabase.option(ApiTokens.filter(_.token === token))
    }
  }

  "#index" should {
    "with a document set" should {
      trait IndexScope extends BaseScope {
        val createdBy = "creator@example.org"
        val documentSet = factory.documentSet()
        val apiToken1 = factory.apiToken(token="tokenA", createdBy=createdBy, documentSetId=Some(documentSet.id))
        val apiToken2 = factory.apiToken(token="tokenB", createdBy=createdBy, documentSetId=Some(documentSet.id))
        lazy val ret = await(backend.index(createdBy, Some(documentSet.id)))
      }

      "return API tokens" in new IndexScope {
        ret must containTheSameElementsAs(Seq(apiToken1, apiToken2))
      }

      "ignore a non-docset ApiToken" in new IndexScope {
        factory.apiToken(token="tokenC", createdBy=createdBy, documentSetId=None)
        ret.length must beEqualTo(2)
      }

      "ignore an ApiToken for a different docset" in new IndexScope {
        val documentSet2 = factory.documentSet()
        factory.apiToken(token="tokenC", createdBy=createdBy, documentSetId=Some(documentSet2.id))
        ret.length must beEqualTo(2)
      }

      "ignore a different user's ApiToken" in new IndexScope {
        factory.apiToken(token="tokenC", createdBy="other@example.org", documentSetId=Some(documentSet.id))
        ret.length must beEqualTo(2)
      }
    }

    "with no document set" should {
      trait IndexScope extends BaseScope {
        val createdBy = "creator@example.org"
        val apiToken1 = factory.apiToken(token="tokenA", createdBy=createdBy, documentSetId=None)
        val apiToken2 = factory.apiToken(token="tokenB", createdBy=createdBy, documentSetId=None)
        lazy val ret = await(backend.index(createdBy, None))
      }

      "return API tokens" in new IndexScope {
        ret must containTheSameElementsAs(Seq(apiToken1, apiToken2))
      }

      "ignore a docset ApiToken" in new IndexScope {
        val documentSet = factory.documentSet()
        factory.apiToken(token="tokenC", createdBy=createdBy, documentSetId=Some(documentSet.id))
        ret.length must beEqualTo(2)
      }

      "ignore a different user's ApiToken" in new IndexScope {
        factory.apiToken(token="tokenC", createdBy="other@example.org", documentSetId=None)
        ret.length must beEqualTo(2)
      }
    }
  }

  "#show" should {
    trait ShowScope extends BaseScope {
      val apiToken = factory.apiToken(token="some-token")
    }

    "return a token" in new ShowScope {
      await(backend.show("some-token")) must beSome(apiToken)
    }

    "return None when a token does not exist" in new ShowScope {
      await(backend.show("some-invalid-token")) must beNone
    }
  }

  "#create" should {
    trait CreateScope extends BaseScope {
      val documentSet = factory.documentSet()
      val documentSetId = Some(documentSet.id)
      val attributes = ApiToken.CreateAttributes(
        email="user@example.org",
        description="description"
      )

      def createApiToken = await(backend.create(documentSetId, attributes))
      lazy val apiToken = createApiToken
    }

    "return an ApiToken" in new CreateScope {
      apiToken.documentSetId must beSome(documentSet.id)
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
      override val documentSetId = Some(documentSet.id + 1L)
      createApiToken must throwA[exceptions.ParentMissing]
    }
  }

  "#destroy" should {
    trait DestroyScope extends BaseScope {
      val documentSet = factory.documentSet()
      val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
      def destroy = await(backend.destroy(apiToken.token))
    }

    "delete the ApiToken" in new DestroyScope {
      destroy
      findApiToken(apiToken.token) must beNone
    }

    "not delete a different ApiToken" in new DestroyScope {
      val apiToken2 = factory.apiToken(documentSetId=Some(documentSet.id), token="token2")
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
