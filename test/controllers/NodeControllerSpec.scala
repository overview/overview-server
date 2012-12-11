package controllers

import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._
import play.api.Play.{start,stop}

import org.overviewproject.test.Specification
import org.overviewproject.tree.orm.Node
import org.squeryl.PrimitiveTypeMode._
import controllers.auth.AuthorizedRequest
import helpers.DbTestContext
import models.OverviewUser
import models.orm.{DocumentSet,User}
import models.orm.DocumentSetType._
import models.orm.Schema.nodes

class NodeControllerSpec extends Specification {
  step(start(FakeApplication()))

  trait ValidUpdateProcess extends DbTestContext {
    // HACK: These are called within setupWithDb()
    lazy val user = OverviewUser(User())
    lazy val documentSet = DocumentSet(DocumentCloudDocumentSet, 0L, "title", Some("query")).save
    lazy val node = nodes.insertOrUpdate(Node(documentSet.id, None, "description", 0, Array[Long]()))

    override def setupWithDb = {
      // TODO: don't rely on DB
      documentSet.nodes.associate(node)
    }

    lazy val request = new AuthorizedRequest(FakeRequest().withFormUrlEncodedBody("description" -> "new description"), user)
  }

  "NodeController" should {
    "edit a node" in new ValidUpdateProcess {
      val result = NodeController.update(documentSet.id, node.id)(request)
      status(result) must beEqualTo(OK)

      val node2 = documentSet.nodes.single
      node2.description must beEqualTo("new description")
    }
  }

  step(stop)
}
