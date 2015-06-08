package controllers.backend

import play.api.libs.json.Json

import org.overviewproject.models.StoreObject
import org.overviewproject.models.tables.StoreObjects

class DbStoreObjectBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbStoreObjectBackend with org.overviewproject.database.DatabaseProvider

    def findStoreObject(id: Long) = {
      import org.overviewproject.database.Slick.simple._
      StoreObjects.filter(_.id === id).firstOption(session)
    }

    def findDocumentStoreObject(documentId: Long, storeObjectId: Long) = {
      import org.overviewproject.database.Slick.simple._
      import org.overviewproject.models.tables.DocumentStoreObjects
      DocumentStoreObjects
        .filter(_.documentId === documentId)
        .filter(_.storeObjectId === storeObjectId)
        .firstOption(session)
    }
  }

  "DbStoreObjectBackend" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)
      }

      "index a store's objects" in new IndexScope {
        val so1 = factory.storeObject(storeId=store.id)
        val so2 = factory.storeObject(storeId=store.id)

        val ret = await(backend.index(store.id))
        ret.length must beEqualTo(2)
        ret.map(_.id) must containTheSameElementsAs(Seq(so1.id, so2.id))
      }

      "filter by indexedLong" in new IndexScope {
        val so1 = factory.storeObject(storeId=store.id, indexedLong=Some(4L))
        val so2 = factory.storeObject(storeId=store.id, indexedLong=Some(6L))

        val ret = await(backend.index(store.id, indexedLong=Some(4L)))
        ret.length must beEqualTo(1)
        ret(0).id must beEqualTo(so1.id)
      }

      "filter by indexedString" in new IndexScope {
        val so1 = factory.storeObject(storeId=store.id, indexedString=Some("foo"))
        val so2 = factory.storeObject(storeId=store.id, indexedString=Some("bar"))

        val ret = await(backend.index(store.id, indexedString=Some("foo")))
        ret.length must beEqualTo(1)
        ret(0).id must beEqualTo(so1.id)
      }

      "filter by both indexedLong and indexedString" in new IndexScope {
        val so1 = factory.storeObject(storeId=store.id, indexedLong=Some(4L), indexedString=Some("foo"))
        val so2 = factory.storeObject(storeId=store.id, indexedLong=Some(4L), indexedString=Some("bar"))
        val so3 = factory.storeObject(storeId=store.id, indexedLong=Some(6L), indexedString=Some("bar"))

        val ret = await(backend.index(store.id, indexedLong=Some(4L), indexedString=Some("bar")))
        ret.length must beEqualTo(1)
        ret(0).id must beEqualTo(so2.id)
      }
    }

    "#show" should {
      "show a StoreObject" in new BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)
        val storeObject = factory.storeObject(storeId=store.id, json=Json.obj("foo" -> "bar"))

        val ret = await(backend.show(storeObject.id))
        ret.map(_.id) must beSome(storeObject.id)
        ret.map(_.json) must beSome(storeObject.json)
      }

      "return None on an invalid StoreObject" in new BaseScope {
        val ret = await(backend.show(123L))
        ret must beNone
      }
    }

    "#create" should {
      trait CreateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)

        val attributes = StoreObject.CreateAttributes(
          indexedLong=Some(4L),
          indexedString=Some("foo"),
          json=Json.obj("foo" -> "bar")
        )

        def createStoreObject = await(backend.create(store.id, attributes))
        lazy val storeObject = createStoreObject
      }

      "return a StoreObject" in new CreateScope {
        storeObject.storeId must beEqualTo(store.id)
        storeObject.indexedLong must beSome(4L)
        storeObject.indexedString must beSome("foo")
        storeObject.json must beEqualTo(Json.obj("foo" -> "bar"))
      }

      "write the StoreObject to the database" in new CreateScope {
        val dbStoreObject = findStoreObject(storeObject.id)
        dbStoreObject.map(_.storeId) must beSome(store.id)
        dbStoreObject.flatMap(_.indexedLong) must beSome(4L)
        dbStoreObject.flatMap(_.indexedString) must beSome("foo")
        dbStoreObject.map(_.json) must beSome(Json.obj("foo" -> "bar"))
      }

      "pick non-conflicting StoreObject IDs" in new CreateScope {
        val ret1 = createStoreObject
        val ret2 = createStoreObject
        ret1.id must not(beEqualTo(ret2.id))
      }
    }

    "#createMany" should {
      trait CreateManyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)

        val attrs1 = StoreObject.CreateAttributes(
          indexedLong=Some(1L),
          indexedString=Some("foo"),
          json=Json.obj("foo" -> "bar")
        )

        val attrs2 = StoreObject.CreateAttributes(
          indexedLong=Some(2L),
          indexedString=Some("bar"),
          json=Json.obj("bar" -> "baz")
        )

        val attributesSeq = Seq(attrs1, attrs2)

        def createMany = await(backend.createMany(store.id, attributesSeq))
        lazy val storeObjects = createMany
      }

      "return StoreObjects" in new CreateManyScope {
        storeObjects.map(_.storeId) must beEqualTo(Seq(store.id, store.id))
        storeObjects.map(_.indexedLong) must beEqualTo(Seq(Some(1L), Some(2L)))
        storeObjects.map(_.indexedString) must beEqualTo(Seq(Some("foo"), Some("bar")))
        storeObjects.map(_.json) must beEqualTo(Seq(Json.obj("foo" -> "bar"), Json.obj("bar" -> "baz")))
      }

      "write the StoreObjects to the database" in new CreateManyScope {
        val dbStoreObject1 = findStoreObject(storeObjects(0).id)
        val dbStoreObject2 = findStoreObject(storeObjects(1).id)
        dbStoreObject1.map(_.storeId) must beSome(store.id)
        dbStoreObject1.flatMap(_.indexedLong) must beSome(1L)
        dbStoreObject2.flatMap(_.indexedString) must beSome("bar")
      }

      "pick non-conflicting IDs" in new CreateManyScope {
        await(backend.create(store.id, attrs2))
        createMany must not(throwA[Exception])
        await(backend.index(store.id)).length must beEqualTo(3)
      }

      "work with an empty list" in new CreateManyScope {
        override val attributesSeq = Seq()
        storeObjects must beEqualTo(Seq())
      }
    }

    "#update" should {
      trait UpdateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)
        val storeObject = factory.storeObject(storeId=store.id)

        val attributes = StoreObject.UpdateAttributes(
          indexedLong=Some(1L),
          indexedString=Some("foo"),
          json=Json.obj("new foo" -> "new bar")
        )
        lazy val newStoreObject = updateStoreObject
        val storeObjectId = storeObject.id
        def updateStoreObject = await(backend.update(storeObjectId, attributes))
      }

      "return a StoreObject" in new UpdateScope {
        newStoreObject.map(_.id) must beSome(storeObject.id)
        newStoreObject.map(_.storeId) must beSome(storeObject.storeId)
      }

      "update the StoreObject" in new UpdateScope {
        updateStoreObject
        val dbStoreObject = findStoreObject(storeObject.id)
        dbStoreObject.map(_.id) must beSome(storeObject.id)
        dbStoreObject.map(_.storeId) must beSome(storeObject.storeId)
        dbStoreObject.map(_.indexedLong) must beSome(attributes.indexedLong)
        dbStoreObject.map(_.indexedString) must beSome(attributes.indexedString)
        dbStoreObject.map(_.json) must beSome(attributes.json)
      }

      "return None when updating a non-StoreObject" in new UpdateScope {
        override val storeObjectId = storeObject.id + 1L
        newStoreObject must beNone
      }

      "not update other StoreObjects" in new UpdateScope {
        val storeObject2 = factory.storeObject(storeId=store.id, json=Json.obj("bar" -> "baz"))
        updateStoreObject
        val dbStoreObject2 = findStoreObject(storeObject2.id)
        dbStoreObject2.map(_.id) must beSome(storeObject2.id)
        dbStoreObject2.map(_.json) must beSome(Json.obj("bar" -> "baz"))
      }
    }

    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)
        val storeObject = factory.storeObject(storeId=store.id)

        def destroy(id: Long) = await(backend.destroy(id))
      }

      "delete a StoreObject from the database" in new DestroyScope {
        destroy(storeObject.id)
        findStoreObject(storeObject.id) must beNone
      }

      "succeed when the StoreObject does not exist" in new DestroyScope {
        destroy(storeObject.id + 1L)
      }

      "not destroy other StoreObjects" in new DestroyScope {
        val storeObject2 = factory.storeObject(storeId=store.id)
        destroy(storeObject.id)
        findStoreObject(storeObject2.id) must beSome
      }

      "destroy associated DocumentStoreObjects" in new DestroyScope {
        val document = factory.document(documentSetId=documentSet.id)
        val dvo = factory.documentStoreObject(documentId=document.id, storeObjectId=storeObject.id)

        destroy(storeObject.id)
        findStoreObject(storeObject.id) must beNone
        findDocumentStoreObject(document.id, storeObject.id) must beNone
      }
    }

    "#destroyMany" should {
      trait DestroyManyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)
        val obj1 = factory.storeObject(storeId=store.id)
        val obj2 = factory.storeObject(storeId=store.id)

        def destroyMany(ids: Long*) = await(backend.destroyMany(store.id, ids.toSeq))
      }

      "delete StoreObjects from the database" in new DestroyManyScope {
        destroyMany(obj1.id, obj2.id) must beEqualTo(())
        findStoreObject(obj1.id) must beNone
        findStoreObject(obj2.id) must beNone
      }

      "ignore missing StoreObjects" in new DestroyManyScope {
        destroyMany(obj1.id, obj2.id + 1) must beEqualTo(())
        findStoreObject(obj1.id) must beNone
        findStoreObject(obj2.id) must beSome
      }

      "ignore StoreObjects from other Views" in new DestroyManyScope {
        val apiToken2 = factory.apiToken(documentSetId=Some(documentSet.id), token="token2")
        val store2 = factory.store(apiToken=apiToken2.token)
        val obj3 = factory.storeObject(storeId=store2.id)

        destroyMany(obj1.id, obj3.id) must beEqualTo(())
        findStoreObject(obj1.id) must beNone
        findStoreObject(obj3.id) must beSome
      }

      "destroy associated DocumentStoreObjects" in new DestroyManyScope {
        val document = factory.document(documentSetId=documentSet.id)
        val dvo = factory.documentStoreObject(documentId=document.id, storeObjectId=obj1.id)

        destroyMany(obj1.id) must beEqualTo(())
        findStoreObject(obj1.id) must beNone
        findDocumentStoreObject(document.id, obj1.id) must beNone
      }
    }
  }
}
