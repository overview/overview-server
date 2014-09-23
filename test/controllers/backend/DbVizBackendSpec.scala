package controllers.backend

import play.api.libs.json.Json

import org.overviewproject.models.Viz
import org.overviewproject.models.tables.Vizs

class DbVizBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbVizBackend

    def findViz(id: Long) = {
      import org.overviewproject.database.Slick.simple._
      Vizs.filter(_.id === id).firstOption(session)
    }
  }

  "DbVizBackend" should {
    "#index" should {
      "index a document set's vizs" in new BaseScope {
        val documentSet = factory.documentSet()
        val viz1 = factory.viz(documentSetId=documentSet.id)
        val viz2 = factory.viz(documentSetId=documentSet.id)

        val ret = await(backend.index(documentSet.id))
        ret.length must beEqualTo(2)
        ret.map(_.toString) must contain(viz1.toString)
        ret.map(_.toString) must contain(viz2.toString)
      }

      "not index a different document set's vizs" in new BaseScope {
        val documentSet1 = factory.documentSet()
        val documentSet2 = factory.documentSet()
        val viz = factory.viz(documentSetId=documentSet2.id)

        val ret = await(backend.index(documentSet1.id))
        ret.length must beEqualTo(0)
      }
    }

    "#show" should {
      "show a viz" in new BaseScope {
        val documentSet = factory.documentSet()
        val viz = factory.viz(documentSetId=documentSet.id)
        val ret = await(backend.show(viz.id))
        ret.map(_.id) must beSome(viz.id)
      }

      "not show a nonexistent viz" in new BaseScope {
        val ret = await(backend.show(1234L))
        ret must beNone
      }
    }

    "#create" should {
      trait CreateScope extends BaseScope {
        val documentSet = factory.documentSet()

        val attributes = Viz.CreateAttributes(
          url="http://example.org",
          apiToken="api-token",
          title="title",
          json=Json.obj("foo" -> "bar")
        )

        def createViz = await(backend.create(documentSet.id, attributes))
        lazy val viz = createViz
      }

      "return a Viz" in new CreateScope {
        viz.url must beEqualTo(attributes.url)
        viz.apiToken must beEqualTo(attributes.apiToken)
        viz.title must beEqualTo(attributes.title)
        viz.json must beEqualTo(attributes.json)
      }

      "write the Viz to the database" in new CreateScope {
        val dbViz = findViz(viz.id)
        dbViz.map(_.url) must beSome(attributes.url)
        dbViz.map(_.apiToken) must beSome(attributes.apiToken)
        dbViz.map(_.title) must beSome(attributes.title)
        dbViz.map(_.json) must beSome(attributes.json)
      }

      "pick a non-conflicting viz ID" in new CreateScope {
        val viz1 = createViz
        val viz2 = createViz
        viz1.id must not(beEqualTo(viz2.id))
      }
    }

    "#update" should {
      trait UpdateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val viz = factory.viz(documentSetId=documentSet.id)
        val attributes = Viz.UpdateAttributes(
          title="new title",
          json=Json.obj("new foo" -> "new bar")
        )
        val vizId = viz.id
        lazy val newViz = updateViz
        def updateViz = await(backend.update(vizId, attributes))
      }

      "return a Viz" in new UpdateScope {
        newViz.map(_.id) must beSome(vizId)
        newViz.map(_.url) must beSome(viz.url)
        newViz.map(_.title) must beSome(attributes.title)
        newViz.map(_.json) must beSome(attributes.json)
      }

      "update a Viz" in new UpdateScope {
        updateViz
        val dbViz = findViz(vizId)
        dbViz.map(_.id) must beSome(vizId)
        dbViz.map(_.url) must beSome(viz.url)
        dbViz.map(_.title) must beSome(attributes.title)
        dbViz.map(_.json) must beSome(attributes.json)
      }

      "return None when updating a non-Viz" in new UpdateScope {
        override val vizId = viz.id + 1L
        newViz must beNone
      }

      "not update other Vizs" in new UpdateScope {
        val viz2 = factory.viz(documentSetId=documentSet.id)
        updateViz
        val dbViz2 = findViz(viz2.id)
        dbViz2.map(_.id) must beSome(viz2.id)
        dbViz2.map(_.url) must beSome(viz2.url)
        dbViz2.map(_.title) must beSome(viz2.title)
        dbViz2.map(_.json) must beSome(viz2.json)
      }
    }
  }
}
