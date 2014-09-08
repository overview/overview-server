package controllers.backend

import play.api.libs.json.{JsObject,Json}

import org.overviewproject.models.DocumentVizObject
import org.overviewproject.models.tables.DocumentVizObjects

class DbDocumentVizObjectBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbDocumentVizObjectBackend

    def findDocumentVizObject(documentId: Long, vizObjectId: Long) = {
      import org.overviewproject.database.Slick.simple._
      DocumentVizObjects
        .where(_.documentId === documentId)
        .where(_.vizObjectId === vizObjectId)
        .firstOption()(session)
    }
  }

  "DbDocumentVizObjectBackend" should {
    "#show" should {
      trait ShowScope extends BaseScope {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id)
        val viz = factory.viz(documentSetId=documentSet.id)
        val vizObject = factory.vizObject(vizId=viz.id)
        val documentVizObject=factory.documentVizObject(
          documentId=document.id,
          vizObjectId=vizObject.id,
          json=Some(Json.obj("foo" -> "bar"))
        )
      }

      "show a DocumentVizObject" in new ShowScope {
        val ret: Option[DocumentVizObject] = await(backend.show(document.id, vizObject.id))
        ret must beSome
        ret.map(_.documentId) must beSome(document.id)
        ret.map(_.vizObjectId) must beSome(vizObject.id)
        ret.map(_.json) must beSome(Some(Json.obj("foo" -> "bar")))
      }

      "return None with the wrong documentId" in new ShowScope {
        await(backend.show(document.id + 1L, vizObject.id)) must beNone
      }

      "return None with the wrong vizObjectId" in new ShowScope {
        await(backend.show(document.id, vizObject.id + 1L)) must beNone
      }
    }

    "#create" should {
      trait CreateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id)
        val viz = factory.viz(documentSetId=documentSet.id)
        val vizObject = factory.vizObject(vizId=viz.id)

        val json: Option[JsObject] = Some(Json.obj("foo" -> "bar"))

        val documentId = document.id
        val vizObjectId = vizObject.id
        def createDocumentVizObject = await(backend.create(documentId, vizObjectId, json))
        lazy val documentVizObject = createDocumentVizObject
      }

      "return a DocumentVizObject" in new CreateScope {
        documentVizObject.documentId must beEqualTo(document.id)
        documentVizObject.vizObjectId must beEqualTo(vizObject.id)
        documentVizObject.json must beSome(Json.obj("foo" -> "bar"))
      }

      "write the DocumentVizObject to the database" in new CreateScope {
        createDocumentVizObject
        val dbDocumentVizObject = findDocumentVizObject(document.id, vizObject.id)
        dbDocumentVizObject.map(_.documentId) must beSome(document.id)
        dbDocumentVizObject.map(_.vizObjectId) must beSome(vizObject.id)
        dbDocumentVizObject.map(_.json) must beSome(Some(Json.obj("foo" -> "bar")))
      }

      "return an error on Conflict" in new CreateScope {
        createDocumentVizObject
        createDocumentVizObject must throwA[exceptions.Conflict]
      }

      "return an error on missing document" in new CreateScope {
        override val documentId = document.id + 1L
        createDocumentVizObject must throwA[exceptions.ParentMissing]
      }

      "return an error on missing vizObject" in new CreateScope {
        override val vizObjectId = vizObject.id + 1L
        createDocumentVizObject must throwA[exceptions.ParentMissing]
      }
    }

    "#createMany" should {
      trait CreateManyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val viz = factory.viz(documentSetId=documentSet.id)
        val vizId = viz.id
        val obj1 = factory.vizObject(vizId=viz.id)
        val obj2 = factory.vizObject(vizId=viz.id)

        val args: Seq[DocumentVizObject] = Seq(
          DocumentVizObject(doc1.id, obj1.id, Some(Json.obj("foo" -> "bar"))),
          DocumentVizObject(doc2.id, obj1.id, Some(Json.obj("bar" -> "baz"))),
          DocumentVizObject(doc1.id, obj2.id, None)
        )

        def createMany = await(backend.createMany(vizId, args))
        lazy val result: Seq[DocumentVizObject] = createMany
      }

      "return DocumentVizObjects" in new CreateManyScope {
        result must containTheSameElementsAs(Seq(
          DocumentVizObject(doc1.id, obj1.id, Some(Json.obj("foo" -> "bar"))),
          DocumentVizObject(doc2.id, obj1.id, Some(Json.obj("bar" -> "baz"))),
          DocumentVizObject(doc1.id, obj2.id, None)
        ))
      }

      "write the DocumentVizObjects to the database" in new CreateManyScope {
        result // resolve
        val dbResults = Seq(
          findDocumentVizObject(doc1.id, obj1.id),
          findDocumentVizObject(doc2.id, obj1.id),
          findDocumentVizObject(doc1.id, obj2.id),
          findDocumentVizObject(doc2.id, obj2.id) // this one is None
        ).flatten
        dbResults must containTheSameElementsAs(result)
      }

      "update on conflict" in new CreateManyScope {
        await(backend.create(doc1.id, obj1.id, None))
        override val args = Seq(
          DocumentVizObject(doc1.id, obj1.id, Some(Json.obj("foo" -> "bar"))),
          DocumentVizObject(doc2.id, obj2.id, None)
        )
        result must containTheSameElementsAs(Seq(
          DocumentVizObject(doc1.id, obj1.id, Some(Json.obj("foo" -> "bar"))),
          DocumentVizObject(doc2.id, obj2.id, None)
        ))
        Seq(
          findDocumentVizObject(doc1.id, obj1.id),
          findDocumentVizObject(doc2.id, obj1.id),
          findDocumentVizObject(doc1.id, obj2.id),
          findDocumentVizObject(doc2.id, obj2.id)
        ).flatten must containTheSameElementsAs(result)
      }

      "filter by DocumentSet ID" in new CreateManyScope {
        val documentSet2 = factory.documentSet()
        val doc3 = factory.document(documentSetId=documentSet2.id)
        override val args = Seq(
          DocumentVizObject(doc1.id, obj1.id, None),
          DocumentVizObject(doc3.id, obj1.id, None)
        )
        result must beEqualTo(Seq(DocumentVizObject(doc1.id, obj1.id, None)))
        findDocumentVizObject(doc1.id, obj1.id) must beSome
        findDocumentVizObject(doc3.id, obj1.id) must beNone
      }

      "filter by Viz ID" in new CreateManyScope {
        val viz2 = factory.viz(documentSetId=documentSet.id)
        val obj3 = factory.vizObject(vizId=viz2.id)
        override val args = Seq(
          DocumentVizObject(doc1.id, obj1.id, None),
          DocumentVizObject(doc1.id, obj3.id, None)
        )
        result must beEqualTo(Seq(DocumentVizObject(doc1.id, obj1.id, None)))
        findDocumentVizObject(doc1.id, obj1.id) must beSome
        findDocumentVizObject(doc1.id, obj3.id) must beNone
      }
    }

    "#update" should {
      trait UpdateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id)
        val viz = factory.viz(documentSetId=documentSet.id)
        val vizObject = factory.vizObject(vizId=viz.id)
        val documentVizObject = factory.documentVizObject(
          documentId=document.id,
          vizObjectId=vizObject.id,
          json=Some(Json.obj("x" -> "y"))
        )
        val json: Option[JsObject] = Some(Json.obj("foo" -> "bar"))

        lazy val newDocumentVizObject = updateDocumentVizObject
        val documentId = document.id
        val vizObjectId = vizObject.id
        def updateDocumentVizObject = await(backend.update(documentId, vizObjectId, json))
      }

      "return a DocumentVizObject" in new UpdateScope {
        newDocumentVizObject.map(_.documentId) must beSome(document.id)
        newDocumentVizObject.map(_.vizObjectId) must beSome(vizObject.id)
        newDocumentVizObject.map(_.json) must beSome(Some(Json.obj("foo" -> "bar")))
      }

      "update the DocumentVizObject" in new UpdateScope {
        updateDocumentVizObject
        val dbDocumentVizObject = findDocumentVizObject(document.id, vizObject.id)
        dbDocumentVizObject.map(_.documentId) must beSome(document.id)
        dbDocumentVizObject.map(_.vizObjectId) must beSome(vizObject.id)
        dbDocumentVizObject.map(_.json) must beSome(Some(Json.obj("foo" -> "bar")))
      }

      "return None when the DocumentVizObject does not exist" in new UpdateScope {
        override val documentId = document.id + 1L
        newDocumentVizObject must beNone
      }

      "not update other DocumentVizObjects" in new UpdateScope {
        val document2 = factory.document(documentSetId=documentSet.id)
        val documentVizObject2 = factory.documentVizObject(document2.id, vizObject.id, None)
        updateDocumentVizObject
        val dbDocumentVizObject2 = findDocumentVizObject(document2.id, vizObject.id)
        dbDocumentVizObject2.map(_.json) must beSome(None)
      }
    }

    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id)
        val viz = factory.viz(documentSetId=documentSet.id)
        val vizObject = factory.vizObject(vizId=viz.id)
        val documentVizObject = factory.documentVizObject(documentId=document.id, vizObjectId=vizObject.id)

        def destroy(documentId: Long, vizObjectId: Long) = await(backend.destroy(documentId, vizObjectId))
      }

      "destroy a DocumentVizObject" in new DestroyScope {
        destroy(document.id, vizObject.id)
        findDocumentVizObject(document.id, vizObject.id) must beNone
      }

      "do nothing when given a nonexistent DocumentVizObject" in new DestroyScope {
        destroy(document.id + 1L, vizObject.id)
        findDocumentVizObject(document.id, vizObject.id) must beSome
      }

      "not destroy other DocumentVizObjects" in new DestroyScope {
        val document2 = factory.document(documentSetId=documentSet.id)
        val documentVizObject2 = factory.documentVizObject(
          documentId=document2.id,
          vizObjectId=vizObject.id
        )
        destroy(document2.id, vizObject.id)
        findDocumentVizObject(document.id, vizObject.id) must beSome
      }
    }
  }
}
