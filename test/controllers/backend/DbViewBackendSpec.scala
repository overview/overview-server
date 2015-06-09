package controllers.backend

import org.overviewproject.models.View
import org.overviewproject.models.tables.Views

class DbViewBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbViewBackend with org.overviewproject.database.DatabaseProvider

    def findView(id: Long) = {
      import databaseApi._
      blockingDatabase.option(Views.filter(_.id === id))
    }
  }

  "DbViewBackend" should {
    "#index" should {
      "index a document set's views" in new BaseScope {
        val documentSet = factory.documentSet()
        val apiToken1 = factory.apiToken(documentSetId=Some(documentSet.id), token="token1")
        val apiToken2 = factory.apiToken(documentSetId=Some(documentSet.id), token="token2")
        val view1 = factory.view(documentSetId=documentSet.id, apiToken=apiToken1.token)
        val view2 = factory.view(documentSetId=documentSet.id, apiToken=apiToken2.token)

        val ret = await(backend.index(documentSet.id))
        ret.length must beEqualTo(2)
        ret.map(_.toString) must contain(view1.toString)
        ret.map(_.toString) must contain(view2.toString)
      }

      "not index a different document set's views" in new BaseScope {
        val documentSet1 = factory.documentSet()
        val documentSet2 = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet2.id))
        val view = factory.view(documentSetId=documentSet2.id, apiToken=apiToken.token)

        val ret = await(backend.index(documentSet1.id))
        ret.length must beEqualTo(0)
      }
    }

    "#show" should {
      "show a view" in new BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
        val ret = await(backend.show(view.id))
        ret.map(_.id) must beSome(view.id)
      }

      "not show a nonexistent view" in new BaseScope {
        val ret = await(backend.show(1234L))
        ret must beNone
      }
    }

    "#create" should {
      trait CreateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id), token="api-token")

        val attributes = View.CreateAttributes(
          url="http://example.org",
          apiToken="api-token",
          title="title"
        )

        def createView = await(backend.create(documentSet.id, attributes))
        lazy val view = createView
      }

      "return a View" in new CreateScope {
        view.url must beEqualTo(attributes.url)
        view.apiToken must beEqualTo(attributes.apiToken)
        view.title must beEqualTo(attributes.title)
      }

      "write the View to the database" in new CreateScope {
        val dbView = findView(view.id)
        dbView.map(_.url) must beSome(attributes.url)
        dbView.map(_.apiToken) must beSome(attributes.apiToken)
        dbView.map(_.title) must beSome(attributes.title)
      }

      "pick a non-conflicting view ID" in new CreateScope {
        val view1 = createView
        val view2 = createView
        view1.id must not(beEqualTo(view2.id))
      }
    }

    "#update" should {
      trait UpdateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
        val attributes = View.UpdateAttributes(title="new title")
        val viewId = view.id
        lazy val newView = updateView
        def updateView = await(backend.update(viewId, attributes))
      }

      "return a View" in new UpdateScope {
        newView.map(_.id) must beSome(viewId)
        newView.map(_.url) must beSome(view.url)
        newView.map(_.title) must beSome(attributes.title)
      }

      "update a View" in new UpdateScope {
        updateView
        val dbView = findView(viewId)
        dbView.map(_.id) must beSome(viewId)
        dbView.map(_.url) must beSome(view.url)
        dbView.map(_.title) must beSome(attributes.title)
      }

      "return None when updating a non-View" in new UpdateScope {
        override val viewId = view.id + 1L
        newView must beNone
      }

      "not update other Views" in new UpdateScope {
        val apiToken2 = factory.apiToken(documentSetId=Some(documentSet.id), token="token2")
        val view2 = factory.view(documentSetId=documentSet.id, apiToken=apiToken2.token)
        updateView
        val dbView2 = findView(view2.id)
        dbView2.map(_.id) must beSome(view2.id)
        dbView2.map(_.url) must beSome(view2.url)
        dbView2.map(_.title) must beSome(view2.title)
      }
    }

    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
        def destroy = await(backend.destroy(view.id))
      }

      "delete the View" in new DestroyScope {
        destroy
        findView(view.id) must beNone
      }

      "not delete a different View" in new DestroyScope {
        val apiToken2 = factory.apiToken(documentSetId=Some(documentSet.id), token="token2")
        val view2 = factory.view(documentSetId=documentSet.id, apiToken=apiToken2.token)
        destroy
        findView(view2.id) must beSome
      }
    }
  }
}
