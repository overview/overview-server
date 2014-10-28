package controllers.backend

import play.api.libs.json.Json

import org.overviewproject.models.Store
import org.overviewproject.models.tables.Stores

class DbStoreBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbStoreBackend

    def findStore(apiToken: String) = {
      import org.overviewproject.database.Slick.simple._
      Stores.filter(_.apiToken === apiToken).firstOption(session)
    }
  }

  "DbStoreBackend" should {
    "#showOrCreate" should {
      trait ShowOrCreateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=documentSet.id)
      }

      "show a Store" in new ShowOrCreateScope {
        val expect = factory.store(apiToken=apiToken.token, json=Json.obj("foo" -> "bar"))
        val actual = await(backend.showOrCreate(apiToken.token))

        actual must beEqualTo(expect)
      }

      "create and return a Store if it does not exist" in new ShowOrCreateScope {
        val actual = await(backend.showOrCreate(apiToken.token))
        val expect = Store(actual.id, apiToken.token, Json.obj())

        actual must beEqualTo(expect)
        findStore(apiToken.token) must beSome(expect)
      }

      "throw ParentMissing when the API Token is invalid" in new ShowOrCreateScope {
        await(backend.showOrCreate(s"not-${apiToken.token}")) must throwA[exceptions.ParentMissing]
      }
    }

    "#upsert" should {
      trait UpdateOrCreateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=documentSet.id)
      }

      "overwrite JSON" in new UpdateOrCreateScope {
        val store = factory.store(apiToken=apiToken.token, json=Json.obj("foo" -> "bar"))
        await(backend.upsert(apiToken.token, Json.obj("bar" -> "baz")))
        findStore(apiToken.token).map(_.json) must beSome(Json.obj("bar" -> "baz"))
      }

      "return the updated Store" in new UpdateOrCreateScope {
        val store = factory.store(apiToken=apiToken.token, json=Json.obj("foo" -> "bar"))
        val result = await(backend.upsert(apiToken.token, Json.obj("bar" -> "baz")))
        Some(result) must beEqualTo(findStore(apiToken.token))
      }

      "create a Store if it is missing" in new UpdateOrCreateScope {
        val result = await(backend.upsert(apiToken.token, Json.obj("foo" -> "baz")))
        result.json must beEqualTo(Json.obj("foo" -> "baz"))
        findStore(apiToken.token).map(_.json) must beSome(Json.obj("foo" -> "baz"))
      }

      "throw ParentMissing when the API Token is invalid" in new UpdateOrCreateScope {
        await(backend.upsert(s"not-${apiToken.token}", Json.obj("foo" -> "baz"))) must throwA[exceptions.ParentMissing]
      }
    }

    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=documentSet.id)
        val store = factory.store(apiToken=apiToken.token)
      }

      "destroy a Store" in new DestroyScope {
        await(backend.destroy(apiToken.token))
        findStore(apiToken.token) must beNone
      }

      "not destroy a Store that never existed" in new DestroyScope {
        await(backend.destroy(s"not-really-${apiToken.token}"))
        findStore(apiToken.token) must beSome
      }
    }
  }
}
