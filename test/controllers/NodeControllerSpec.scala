package controllers

import org.specs2.specification.Scope
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._
import play.api.Play.{start,stop}

import org.overviewproject.test.IdGenerator._
import org.overviewproject.test.Specification

import controllers.auth.AuthorizedRequest
import org.overviewproject.tree.orm.Node
import models.OverviewUser
import models.orm.User

class NodeControllerSpec extends Specification {
  step(start(FakeApplication()))

  class TestNodeController(val node: Node) extends NodeController {
    var savedNode : Option[Node] = None

    override def findNode(documentSetId: Long, id: Long) = {
      if (documentSetId == node.documentSetId && id == node.id) {
        Some(node)
      } else {
        None
      }
    }

    override def saveNode(node: Node) = {
      savedNode = Some(node)
      node
    }
  }

  trait TestScope extends Scope {
    lazy val node = Node(
      id=1L,
      documentSetId=1L,
      parentId=None,
      description="description",
      cachedSize=0,
      cachedDocumentIds=Array[Long]()
    )
    lazy val user = OverviewUser(User())
    lazy val controller = new TestNodeController(node)
    def getRequest = new AuthorizedRequest(FakeRequest(), user)
    def postRequest = new AuthorizedRequest(FakeRequest().withFormUrlEncodedBody("description" -> "new description"), user)

    def index(documentSetId: Long) = controller.index(documentSetId)(getRequest)
    def show(documentSetId: Long, nodeId: Long) = controller.show(documentSetId, nodeId)(getRequest)
    def update(documentSetId: Long, nodeId: Long) = controller.update(documentSetId, nodeId)(postRequest)
  }

  "NodeController" should {
    "edit a node" in new TestScope {
      val result = update(node.documentSetId, node.id)
      status(result) must beEqualTo(OK)
      controller.savedNode.map(_.description).getOrElse("") must beEqualTo("new description")
    }

    "return NotFound when a node isn't found" in new TestScope {
      val result = update(node.documentSetId + 1, node.id) // invalid
      status(result) must beEqualTo(NOT_FOUND)
    }

    // TODO test show() and index(). First, make them use Squeryl?
  }

  step(stop)
}
