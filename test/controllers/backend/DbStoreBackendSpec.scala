package controllers.backend

import play.api.libs.json.Json

import org.overviewproject.database.exceptions
import org.overviewproject.models.Store
import org.overviewproject.models.tables.{DocumentStoreObjects,StoreObjects,Stores}

class DbStoreBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbStoreBackend with org.overviewproject.database.DatabaseProvider

    def findStore(apiToken: String) = {
      import databaseApi._
      blockingDatabase.option(Stores.filter(_.apiToken === apiToken))
    }

    def findStore(id: Long) = {
      import databaseApi._
      blockingDatabase.option(Stores.filter(_.id === id))
    }

    def findStoreObject(id: Long) = {
      import databaseApi._
      blockingDatabase.option(StoreObjects.filter(_.id === id))
    }

    def findDocumentStoreObjects(storeObjectId: Long) = {
      import databaseApi._
      blockingDatabase.seq(DocumentStoreObjects.filter(_.storeObjectId === storeObjectId))
    }
  }

  "DbStoreBackend" should {
    "#showOrCreate" should {
      trait ShowOrCreateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
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
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
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
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)

        def destroy = await(backend.destroy(store.apiToken))
      }

      "destroy a Store" in new DestroyScope {
        await(backend.destroy(apiToken.token))
        findStore(apiToken.token) must beNone
      }

      "not destroy a Store that never existed" in new DestroyScope {
        await(backend.destroy(s"not-really-${apiToken.token}"))
        findStore(apiToken.token) must beSome
      }

      "delete the StoreObjects and DocumentStoreObjects" in new DestroyScope {
        val storeObject = factory.storeObject(storeId=store.id)
        val storeObject2 = factory.storeObject(storeId=store.id)
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val so1 = factory.documentStoreObject(doc1.id, storeObject.id)
        val so2 = factory.documentStoreObject(doc2.id, storeObject.id)
        val so3 = factory.documentStoreObject(doc2.id, storeObject2.id)

        destroy

        findStore(store.id) must beNone
        findStoreObject(storeObject.id) must beNone
        findStoreObject(storeObject2.id) must beNone
        findDocumentStoreObjects(storeObject.id) must beEmpty
        findDocumentStoreObjects(storeObject2.id) must beEmpty
      }

      "not delete another Store's StoreObjects and DocumentStoreObjects" in new DestroyScope {
        val apiToken2 = factory.apiToken(documentSetId=Some(documentSet.id), token="token2")
        val view2 = factory.view(documentSetId=documentSet.id, apiToken=apiToken2.token)
        val store2 = factory.store(apiToken=apiToken2.token)
        val storeObject = factory.storeObject(storeId=store2.id)
        val storeObject2 = factory.storeObject(storeId=store2.id)
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val so1 = factory.documentStoreObject(doc1.id, storeObject.id)
        val so2 = factory.documentStoreObject(doc2.id, storeObject.id)
        val so3 = factory.documentStoreObject(doc2.id, storeObject2.id)

        destroy

        findStore(store2.id) must beSome
        findStoreObject(storeObject.id) must beSome
        findStoreObject(storeObject2.id) must beSome
        findDocumentStoreObjects(storeObject.id) must not(beEmpty)
        findDocumentStoreObjects(storeObject2.id) must not(beEmpty)
      }
    }
  }
}
