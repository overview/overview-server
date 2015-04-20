package controllers.backend

import play.api.libs.json.{JsObject,Json}

import models.{InMemorySelection,Selection,SelectionRequest}
import org.overviewproject.models.DocumentStoreObject
import org.overviewproject.models.tables.DocumentStoreObjects

class DbDocumentStoreObjectBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbDocumentStoreObjectBackend

    def findDocumentStoreObject(documentId: Long, storeObjectId: Long) = {
      import org.overviewproject.database.Slick.simple._
      DocumentStoreObjects
        .filter(_.documentId === documentId)
        .filter(_.storeObjectId === storeObjectId)
        .firstOption(session)
    }
  }

  "DbDocumentStoreObjectBackend" should {
    "#show" should {
      trait ShowScope extends BaseScope {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id)
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)
        val storeObject = factory.storeObject(storeId=store.id)
        val documentStoreObject=factory.documentStoreObject(
          documentId=document.id,
          storeObjectId=storeObject.id,
          json=Some(Json.obj("foo" -> "bar"))
        )
      }

      "show a DocumentStoreObject" in new ShowScope {
        val ret: Option[DocumentStoreObject] = await(backend.show(document.id, storeObject.id))
        ret must beSome
        ret.map(_.documentId) must beSome(document.id)
        ret.map(_.storeObjectId) must beSome(storeObject.id)
        ret.map(_.json) must beSome(Some(Json.obj("foo" -> "bar")))
      }

      "return None with the wrong documentId" in new ShowScope {
        await(backend.show(document.id + 1L, storeObject.id)) must beNone
      }

      "return None with the wrong storeObjectId" in new ShowScope {
        await(backend.show(document.id, storeObject.id + 1L)) must beNone
      }
    }

    "#countByObject" should {
      trait CountByObjectScope extends BaseScope {
        val documentSet = factory.documentSet()
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val doc3 = factory.document(documentSetId=documentSet.id) // no joins
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)
        val so1 = factory.storeObject(storeId=store.id)
        val so2 = factory.storeObject(storeId=store.id)
        val so3 = factory.storeObject(storeId=store.id) // no joins

        val dso11 = factory.documentStoreObject(documentId=doc1.id, storeObjectId=so1.id)
        val dso12 = factory.documentStoreObject(documentId=doc1.id, storeObjectId=so2.id)
        val dso21 = factory.documentStoreObject(documentId=doc2.id, storeObjectId=so1.id)

        val selection: Selection = InMemorySelection(Seq(doc1.id, doc2.id, doc3.id))

        lazy val result: Map[Long,Int] = await(backend.countByObject(store.id, selection))
      }

      "return counts for StoreObjects that have counts" in new CountByObjectScope {
        result(so1.id) must beEqualTo(2)
        result(so2.id) must beEqualTo(1)
      }

      "skip counts for StoreObjects with no documents" in new CountByObjectScope {
        result.isDefinedAt(so3.id) must beFalse
      }

      "filter by the Selection only includes a subset" in new CountByObjectScope {
        override val selection = InMemorySelection(Seq(doc2.id))
        result(so1.id) must beEqualTo(1)
        result.isDefinedAt(so2.id) must beFalse
        result.isDefinedAt(so3.id) must beFalse
      }
    }

    "#create" should {
      trait CreateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id)
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)
        val storeObject = factory.storeObject(storeId=store.id)

        val json: Option[JsObject] = Some(Json.obj("foo" -> "bar"))

        val documentId = document.id
        val storeObjectId = storeObject.id
        def createDocumentStoreObject = await(backend.create(documentId, storeObjectId, json))
        lazy val documentStoreObject = createDocumentStoreObject
      }

      "return a DocumentStoreObject" in new CreateScope {
        documentStoreObject.documentId must beEqualTo(document.id)
        documentStoreObject.storeObjectId must beEqualTo(storeObject.id)
        documentStoreObject.json must beSome(Json.obj("foo" -> "bar"))
      }

      "write the DocumentStoreObject to the database" in new CreateScope {
        createDocumentStoreObject
        val dbDocumentStoreObject = findDocumentStoreObject(document.id, storeObject.id)
        dbDocumentStoreObject.map(_.documentId) must beSome(document.id)
        dbDocumentStoreObject.map(_.storeObjectId) must beSome(storeObject.id)
        dbDocumentStoreObject.map(_.json) must beSome(Some(Json.obj("foo" -> "bar")))
      }

      "return an error on Conflict" in new CreateScope {
        createDocumentStoreObject
        createDocumentStoreObject must throwA[exceptions.Conflict]
      }

      "return an error on missing document" in new CreateScope {
        override val documentId = document.id + 1L
        createDocumentStoreObject must throwA[exceptions.ParentMissing]
      }

      "return an error on missing storeObject" in new CreateScope {
        override val storeObjectId = storeObject.id + 1L
        createDocumentStoreObject must throwA[exceptions.ParentMissing]
      }
    }

    "#createMany" should {
      trait CreateManyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)
        val storeId = store.id
        val obj1 = factory.storeObject(storeId=store.id)
        val obj2 = factory.storeObject(storeId=store.id)

        val args: Seq[DocumentStoreObject] = Seq(
          DocumentStoreObject(doc1.id, obj1.id, Some(Json.obj("foo" -> "bar"))),
          DocumentStoreObject(doc2.id, obj1.id, Some(Json.obj("bar" -> "baz"))),
          DocumentStoreObject(doc1.id, obj2.id, None)
        )

        def createMany = await(backend.createMany(storeId, args))
        lazy val result: Seq[DocumentStoreObject] = createMany
      }

      "return DocumentStoreObjects" in new CreateManyScope {
        result must containTheSameElementsAs(Seq(
          DocumentStoreObject(doc1.id, obj1.id, Some(Json.obj("foo" -> "bar"))),
          DocumentStoreObject(doc2.id, obj1.id, Some(Json.obj("bar" -> "baz"))),
          DocumentStoreObject(doc1.id, obj2.id, None)
        ))
      }

      "write the DocumentStoreObjects to the database" in new CreateManyScope {
        result // resolve
        val dbResults = Seq(
          findDocumentStoreObject(doc1.id, obj1.id),
          findDocumentStoreObject(doc2.id, obj1.id),
          findDocumentStoreObject(doc1.id, obj2.id),
          findDocumentStoreObject(doc2.id, obj2.id) // this one is None
        ).flatten
        dbResults must containTheSameElementsAs(result)
      }

      "update on conflict" in new CreateManyScope {
        await(backend.create(doc1.id, obj1.id, None))
        override val args = Seq(
          DocumentStoreObject(doc1.id, obj1.id, Some(Json.obj("foo" -> "bar"))),
          DocumentStoreObject(doc2.id, obj2.id, None)
        )
        result must containTheSameElementsAs(Seq(
          DocumentStoreObject(doc1.id, obj1.id, Some(Json.obj("foo" -> "bar"))),
          DocumentStoreObject(doc2.id, obj2.id, None)
        ))
        Seq(
          findDocumentStoreObject(doc1.id, obj1.id),
          findDocumentStoreObject(doc2.id, obj1.id),
          findDocumentStoreObject(doc1.id, obj2.id),
          findDocumentStoreObject(doc2.id, obj2.id)
        ).flatten must containTheSameElementsAs(result)
      }

      "filter by DocumentSet ID" in new CreateManyScope {
        val documentSet2 = factory.documentSet()
        val doc3 = factory.document(documentSetId=documentSet2.id)
        override val args = Seq(
          DocumentStoreObject(doc1.id, obj1.id, None),
          DocumentStoreObject(doc3.id, obj1.id, None)
        )
        result must beEqualTo(Seq(DocumentStoreObject(doc1.id, obj1.id, None)))
        findDocumentStoreObject(doc1.id, obj1.id) must beSome
        findDocumentStoreObject(doc3.id, obj1.id) must beNone
      }

      "filter by Store ID" in new CreateManyScope {
        val apiToken2 = factory.apiToken(documentSetId=Some(documentSet.id), token="token2")
        val store2 = factory.store(apiToken=apiToken2.token)
        val obj3 = factory.storeObject(storeId=store2.id)
        override val args = Seq(
          DocumentStoreObject(doc1.id, obj1.id, None),
          DocumentStoreObject(doc1.id, obj3.id, None)
        )
        result must beEqualTo(Seq(DocumentStoreObject(doc1.id, obj1.id, None)))
        findDocumentStoreObject(doc1.id, obj1.id) must beSome
        findDocumentStoreObject(doc1.id, obj3.id) must beNone
      }
    }

    "#update" should {
      trait UpdateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id)
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)
        val storeObject = factory.storeObject(storeId=store.id)
        val documentStoreObject = factory.documentStoreObject(
          documentId=document.id,
          storeObjectId=storeObject.id,
          json=Some(Json.obj("x" -> "y"))
        )
        val json: Option[JsObject] = Some(Json.obj("foo" -> "bar"))

        lazy val newDocumentStoreObject = updateDocumentStoreObject
        val documentId = document.id
        val storeObjectId = storeObject.id
        def updateDocumentStoreObject = await(backend.update(documentId, storeObjectId, json))
      }

      "return a DocumentStoreObject" in new UpdateScope {
        newDocumentStoreObject.map(_.documentId) must beSome(document.id)
        newDocumentStoreObject.map(_.storeObjectId) must beSome(storeObject.id)
        newDocumentStoreObject.map(_.json) must beSome(Some(Json.obj("foo" -> "bar")))
      }

      "update the DocumentStoreObject" in new UpdateScope {
        updateDocumentStoreObject
        val dbDocumentStoreObject = findDocumentStoreObject(document.id, storeObject.id)
        dbDocumentStoreObject.map(_.documentId) must beSome(document.id)
        dbDocumentStoreObject.map(_.storeObjectId) must beSome(storeObject.id)
        dbDocumentStoreObject.map(_.json) must beSome(Some(Json.obj("foo" -> "bar")))
      }

      "return None when the DocumentStoreObject does not exist" in new UpdateScope {
        override val documentId = document.id + 1L
        newDocumentStoreObject must beNone
      }

      "not update other DocumentStoreObjects" in new UpdateScope {
        val document2 = factory.document(documentSetId=documentSet.id)
        val documentStoreObject2 = factory.documentStoreObject(document2.id, storeObject.id, None)
        updateDocumentStoreObject
        val dbDocumentStoreObject2 = findDocumentStoreObject(document2.id, storeObject.id)
        dbDocumentStoreObject2.map(_.json) must beSome(None)
      }
    }

    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id)
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)
        val storeObject = factory.storeObject(storeId=store.id)
        val documentStoreObject = factory.documentStoreObject(documentId=document.id, storeObjectId=storeObject.id)

        def destroy(documentId: Long, storeObjectId: Long) = await(backend.destroy(documentId, storeObjectId))
      }

      "destroy a DocumentStoreObject" in new DestroyScope {
        destroy(document.id, storeObject.id)
        findDocumentStoreObject(document.id, storeObject.id) must beNone
      }

      "do nothing when given a nonexistent DocumentStoreObject" in new DestroyScope {
        destroy(document.id + 1L, storeObject.id)
        findDocumentStoreObject(document.id, storeObject.id) must beSome
      }

      "not destroy other DocumentStoreObjects" in new DestroyScope {
        val document2 = factory.document(documentSetId=documentSet.id)
        val documentStoreObject2 = factory.documentStoreObject(
          documentId=document2.id,
          storeObjectId=storeObject.id
        )
        destroy(document2.id, storeObject.id)
        findDocumentStoreObject(document.id, storeObject.id) must beSome
      }
    }

    "#destroyMany" should {
      trait DestroyManyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val store = factory.store(apiToken=apiToken.token)
        val obj1 = factory.storeObject(storeId=store.id)
        val obj2 = factory.storeObject(storeId=store.id)
        val dvo1 = factory.documentStoreObject(documentId=doc1.id, storeObjectId=obj1.id)
        val dvo2 = factory.documentStoreObject(documentId=doc2.id, storeObjectId=obj1.id)
        val dvo3 = factory.documentStoreObject(documentId=doc1.id, storeObjectId=obj2.id)

        val arg: Seq[(Long,Long)] = Seq(doc1.id -> obj1.id, doc2.id -> obj1.id)

        lazy val result = await(backend.destroyMany(store.id, arg))
      }

      "destroy DocumentStoreObjects" in new DestroyManyScope {
        result must beEqualTo(())

        findDocumentStoreObject(doc1.id, obj1.id) must beNone
        findDocumentStoreObject(doc2.id, obj1.id) must beNone
        findDocumentStoreObject(doc1.id, obj2.id) must beSome
      }

      "skip documents in other document sets" in new DestroyManyScope {
        val documentSet2 = factory.documentSet()
        val doc3 = factory.document(documentSetId=documentSet.id)
        val apiToken2 = factory.apiToken(documentSetId=Some(documentSet.id), token="token2")
        val store2 = factory.store(apiToken=apiToken2.token)
        val obj3 = factory.storeObject(storeId=store2.id)
        val dvo4 = factory.documentStoreObject(documentId=doc3.id, storeObjectId=obj3.id)

        override val arg = Seq(doc1.id -> obj1.id, doc2.id -> obj1.id, doc3.id -> obj3.id)
        result must beEqualTo(())

        findDocumentStoreObject(doc1.id, obj1.id) must beNone
        findDocumentStoreObject(doc2.id, obj1.id) must beNone
        findDocumentStoreObject(doc3.id, obj3.id) must beSome
      }

      "skip missing rows" in new DestroyManyScope {
        override val arg = Seq(doc1.id -> obj1.id, doc2.id -> obj2.id)
        result must beEqualTo(())

        findDocumentStoreObject(doc1.id, obj1.id) must beNone
        findDocumentStoreObject(doc2.id, obj2.id) must beNone
      }

      "skip objects in other stores" in new DestroyManyScope {
        val apiToken2 = factory.apiToken(documentSetId=Some(documentSet.id), token="token2")
        val store2 = factory.store(apiToken=apiToken2.token)
        val obj3 = factory.storeObject(storeId=store2.id)
        val dvo4 = factory.documentStoreObject(documentId=doc1.id, storeObjectId=obj3.id)

        override val arg = Seq(doc1.id -> obj1.id, doc1.id -> obj3.id)
        result must beEqualTo(())

        findDocumentStoreObject(doc1.id, obj1.id) must beNone
        findDocumentStoreObject(doc1.id, obj3.id) must beSome
      }
    }
  }
}
