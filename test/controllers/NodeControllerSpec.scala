package controllers

import org.specs2.mutable.Specification
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._
import play.api.Play.{start,stop}

import helpers.DbTestContext
import models.OverviewUser
import models.orm.{DocumentSet,Node,User}
import models.orm.DocumentSetType._

class NodeControllerSpec extends Specification {
  step(start(FakeApplication()))

  trait ValidUpdateProcess extends DbTestContext {
    // HACK: These are called within setupWithDb()
    lazy val ormUser = User().save
    lazy val user = OverviewUser(ormUser)
    lazy val documentSet = DocumentSet(DocumentCloudDocumentSet, 0L, "title", Some("query")).save
    lazy val node = Node(0L, documentSet.id, "description").save

    override def setupWithDb = {
      // TODO: don't rely on DB
      ormUser.documentSets.associate(documentSet)
      documentSet.nodes.associate(node)
    }

    implicit lazy val request = FakeRequest().withFormUrlEncodedBody("description" -> "new description")
  }

  "NodeController" should {
    "edit a node" in new ValidUpdateProcess {
      val result = NodeController.authorizedUpdate(user, documentSet.id, node.id)
      val node2 = documentSet.nodes.single
      node2.description must be equalTo("new description")
    }
  }

  step(stop)
}
