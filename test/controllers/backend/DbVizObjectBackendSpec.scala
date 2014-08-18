package controllers.backend

import play.api.libs.json.Json

import org.overviewproject.models.VizObject
import org.overviewproject.models.tables.VizObjects

class DbVizObjectBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbVizObjectBackend

    def findVizObject(id: Long) = {
      import org.overviewproject.database.Slick.simple._
      VizObjects.where(_.id === id).firstOption()(session)
    }
  }

  "DbVizObjectBackend" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val documentSet = factory.documentSet()
        val viz = factory.viz(documentSetId=documentSet.id)
      }

      "index a viz's objects" in new IndexScope {
        val vo1 = factory.vizObject(vizId=viz.id)
        val vo2 = factory.vizObject(vizId=viz.id)

        val ret = await(backend.index(viz.id))
        ret.length must beEqualTo(2)
        ret.map(_.id) must containTheSameElementsAs(Seq(vo1.id, vo2.id))
      }

      "filter by indexedLong" in new IndexScope {
        val vo1 = factory.vizObject(vizId=viz.id, indexedLong=Some(4L))
        val vo2 = factory.vizObject(vizId=viz.id, indexedLong=Some(6L))

        val ret = await(backend.index(viz.id, indexedLong=Some(4L)))
        ret.length must beEqualTo(1)
        ret(0).id must beEqualTo(vo1.id)
      }

      "filter by indexedString" in new IndexScope {
        val vo1 = factory.vizObject(vizId=viz.id, indexedString=Some("foo"))
        val vo2 = factory.vizObject(vizId=viz.id, indexedString=Some("bar"))

        val ret = await(backend.index(viz.id, indexedString=Some("foo")))
        ret.length must beEqualTo(1)
        ret(0).id must beEqualTo(vo1.id)
      }

      "filter by both indexedLong and indexedString" in new IndexScope {
        val vo1 = factory.vizObject(vizId=viz.id, indexedLong=Some(4L), indexedString=Some("foo"))
        val vo2 = factory.vizObject(vizId=viz.id, indexedLong=Some(4L), indexedString=Some("bar"))
        val vo3 = factory.vizObject(vizId=viz.id, indexedLong=Some(6L), indexedString=Some("bar"))

        val ret = await(backend.index(viz.id, indexedLong=Some(4L), indexedString=Some("bar")))
        ret.length must beEqualTo(1)
        ret(0).id must beEqualTo(vo2.id)
      }
    }

    "#show" should {
      "show a VizObject" in new BaseScope {
        val documentSet = factory.documentSet()
        val viz = factory.viz(documentSetId=documentSet.id)
        val vizObject = factory.vizObject(vizId=viz.id, json=Json.obj("foo" -> "bar"))

        val ret = await(backend.show(vizObject.id))
        ret.map(_.id) must beSome(vizObject.id)
        ret.map(_.json) must beSome(vizObject.json)
      }

      "return None on an invalid VizObject" in new BaseScope {
        val ret = await(backend.show(123L))
        ret must beNone
      }
    }

    "#create" should {
      trait CreateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val viz = factory.viz(documentSetId=documentSet.id)

        val attributes = VizObject.CreateAttributes(
          indexedLong=Some(4L),
          indexedString=Some("foo"),
          json=Json.obj("foo" -> "bar")
        )

        def createVizObject = await(backend.create(viz.id, attributes))
        lazy val vizObject = createVizObject
      }

      "return a VizObject" in new CreateScope {
        vizObject.vizId must beEqualTo(viz.id)
        vizObject.indexedLong must beSome(4L)
        vizObject.indexedString must beSome("foo")
        vizObject.json must beEqualTo(Json.obj("foo" -> "bar"))
      }

      "write the VizObject to the database" in new CreateScope {
        val dbVizObject = findVizObject(vizObject.id)
        dbVizObject.map(_.vizId) must beSome(viz.id)
        dbVizObject.flatMap(_.indexedLong) must beSome(4L)
        dbVizObject.flatMap(_.indexedString) must beSome("foo")
        dbVizObject.map(_.json) must beSome(Json.obj("foo" -> "bar"))
      }

      "pick a non-conflicting viz ID" in new CreateScope {
        val ret1 = createVizObject
        val ret2 = createVizObject
        ret1.id must not(beEqualTo(ret2.id))
      }
    }

    "#update" should {
      trait UpdateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val viz = factory.viz(documentSetId=documentSet.id)
        val vizObject = factory.vizObject(vizId=viz.id)

        val attributes = VizObject.UpdateAttributes(
          indexedLong=Some(1L),
          indexedString=Some("foo"),
          json=Json.obj("new foo" -> "new bar")
        )
        lazy val newVizObject = updateVizObject
        val vizObjectId = vizObject.id
        def updateVizObject = await(backend.update(vizObjectId, attributes))
      }

      "return a VizObject" in new UpdateScope {
        newVizObject.map(_.id) must beSome(vizObject.id)
        newVizObject.map(_.vizId) must beSome(vizObject.vizId)
      }

      "update the VizObject" in new UpdateScope {
        updateVizObject
        val dbVizObject = findVizObject(vizObject.id)
        dbVizObject.map(_.id) must beSome(vizObject.id)
        dbVizObject.map(_.vizId) must beSome(vizObject.vizId)
        dbVizObject.map(_.indexedLong) must beSome(attributes.indexedLong)
        dbVizObject.map(_.indexedString) must beSome(attributes.indexedString)
        dbVizObject.map(_.json) must beSome(attributes.json)
      }

      "return None when updating a non-VizObject" in new UpdateScope {
        override val vizObjectId = vizObject.id + 1L
        newVizObject must beNone
      }

      "not update other VizObjects" in new UpdateScope {
        val vizObject2 = factory.vizObject(vizId=viz.id, json=Json.obj("bar" -> "baz"))
        updateVizObject
        val dbVizObject2 = findVizObject(vizObject2.id)
        dbVizObject2.map(_.id) must beSome(vizObject2.id)
        dbVizObject2.map(_.json) must beSome(Json.obj("bar" -> "baz"))
      }
    }

    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val viz = factory.viz(documentSetId=documentSet.id)
        val vizObject = factory.vizObject(vizId=viz.id)

        def destroy(id: Long) = await(backend.destroy(id))
      }

      "delete a VizObject from the database" in new DestroyScope {
        destroy(vizObject.id)
        findVizObject(vizObject.id) must beNone
      }

      "succeed when the VizObject does not exist" in new DestroyScope {
        destroy(vizObject.id + 1L)
      }

      "not destroy other VizObjects" in new DestroyScope {
        val vizObject2 = factory.vizObject(vizId=viz.id)
        destroy(vizObject.id)
        findVizObject(vizObject2.id) must beSome
      }
    }
  }
}
