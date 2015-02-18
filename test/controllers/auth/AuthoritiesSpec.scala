package controllers.auth

import java.sql.Connection
import org.specs2.mutable.BeforeAfter
import org.specs2.specification.{Fragments, Step}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await,Future}
import scala.slick.jdbc.UnmanagedSession

import models.{OverviewUser,User,UserRole} // FIXME just use User
import org.overviewproject.database.Slick.simple.Session
import org.overviewproject.database.DB
import org.overviewproject.test.DbSpecification
import org.overviewproject.test.factories.DbFactory

/** Tests authorities.
  *
  * Authorities usually work by performing a SELECT on the database. If a
  * particular combination of database rows exists, the test passes; otherwise
  * it fails.
  *
  * These tests insert extraneous rows into the database in an attempt to fool
  * the Authorities.
  */
class AuthoritiesSpec extends DbSpecification {
  sequential

  override def map(fs: => Fragments) = {
    Step(setupDb) ^ super.map(fs) ^ Step(shutdownDb)
  }

  class TestAuthorities(val session: Session) extends Authorities {
    override def db[A](block: Session => A) = block(session)
  }

  trait DbScope extends BeforeAfter {
    var connected = false
    lazy val connection: Connection = {
      connected = true
      val ret = DB.getConnection()
      ret.setAutoCommit(false)
      ret
    }
    lazy val session: Session = new UnmanagedSession(connection)
    lazy val factory = new DbFactory(connection)

    def await[A](f: Future[A]) = Await.result(f, Duration.Inf)

    override def before = ()

    override def after = {
      if (connected) {
        connection.rollback()
        connection.close()
      }
    }
  }

  trait BaseScope extends DbScope {
    lazy val auth = new TestAuthorities(session)
  }

  "Authorities" should {
    "with a User" should {
      trait UserScope extends BaseScope {
        val goodUser = OverviewUser(User(email="good@example.org"))
        val badUser = OverviewUser(User(email="bad@example.org"))
      }

      "anyUser" should {
        "return true" in new UserScope {
          auth.anyUser(goodUser) must beTrue
        }
      }

      "adminUser" should {
        "return true for an admin" in new UserScope {
          auth.adminUser(OverviewUser(User(role=UserRole.Administrator))) must beTrue
        }

        "return false for a non-admin" in new UserScope {
          auth.adminUser(OverviewUser(User(role=UserRole.NormalUser))) must beFalse
        }
      }

      "userOwningDocumentSet" should {
        "return true when the user owns the document set" in new UserScope {
          val documentSet = factory.documentSet()
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningDocumentSet(documentSet.id)(goodUser) must beTrue
        }

        "return false when another user owns the document set" in new UserScope {
          val documentSet = factory.documentSet()
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningDocumentSet(documentSet.id)(badUser) must beFalse
        }

        "return false when the user owns another document set" in new UserScope {
          val documentSet = factory.documentSet()
          val otherDocumentSet = factory.documentSet()
          factory.documentSetUser(otherDocumentSet.id, goodUser.email)
          auth.userOwningDocumentSet(documentSet.id)(badUser) must beFalse
        }
      }

      "userOwningTag" should {
        "return true when the user owns the document set and tag" in new UserScope {
          val documentSet = factory.documentSet()
          val tag = factory.tag(documentSetId=documentSet.id)
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningTag(documentSet.id, tag.id)(goodUser) must beTrue
        }

        "return false then the user owns the tag in a different document set" in new UserScope {
          val documentSet = factory.documentSet()
          val otherDocumentSet = factory.documentSet()
          val tag = factory.tag(documentSetId=otherDocumentSet.id)
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningTag(documentSet.id, tag.id)(goodUser) must beFalse
        }

        "return false when another user owns the document set" in new UserScope {
          val documentSet = factory.documentSet()
          val tag = factory.tag(documentSetId=documentSet.id)
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningTag(documentSet.id, tag.id)(badUser) must beFalse
        }
      }

      "userOwningTree" should {
        "return true when the user owns the document set and tree" in new UserScope {
          val documentSet = factory.documentSet()
          val node = factory.node(parentId=None)
          val tree = factory.tree(documentSetId=documentSet.id, rootNodeId=node.id)
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningTree(tree.id)(goodUser) must beTrue
        }

        "return false when the user owns a tree in a different document set" in new UserScope {
          val documentSet = factory.documentSet()
          val otherDocumentSet = factory.documentSet()
          val node = factory.node(parentId=None)
          val tree = factory.tree(documentSetId=otherDocumentSet.id, rootNodeId=node.id)
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningTree(tree.id)(goodUser) must beFalse
        }

        "return false when the tree does not exist" in new UserScope {
          val documentSet = factory.documentSet()
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningTree(1L)(goodUser) must beFalse
        }

        "return false when a different user owns the tree" in new UserScope {
          val documentSet = factory.documentSet()
          val node = factory.node(parentId=None)
          val tree = factory.tree(documentSetId=documentSet.id, rootNodeId=node.id)
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningTree(tree.id)(badUser) must beFalse
        }
      }

      "userOwningView" should {
        "return true when the user owns the document set and view" in new UserScope {
          val documentSet = factory.documentSet()
          val apiToken = factory.apiToken(documentSetId=documentSet.id)
          val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningView(view.id)(goodUser) must beTrue
        }

        "return false when the user owns a view in a different document set" in new UserScope {
          val documentSet = factory.documentSet()
          val otherDocumentSet = factory.documentSet()
          val otherApiToken = factory.apiToken(documentSetId=otherDocumentSet.id)
          val view = factory.view(documentSetId=otherDocumentSet.id, apiToken=otherApiToken.token)
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningView(view.id)(goodUser) must beFalse
        }

        "return false when the view does not exist" in new UserScope {
          val documentSet = factory.documentSet()
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningView(1L)(goodUser) must beFalse
        }

        "return false when a different user owns the view" in new UserScope {
          val documentSet = factory.documentSet()
          val apiToken = factory.apiToken(documentSetId=documentSet.id)
          val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningView(view.id)(badUser) must beFalse
        }
      }

      "userOwningStoreObject" should {
        // it just returns false
      }

      "userOwningDocument" should {
        "return true when the user owns the document set" in new UserScope {
          val documentSet = factory.documentSet()
          val document = factory.document(documentSetId=documentSet.id)
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningDocument(document.id)(goodUser) must beTrue
        }

        "return false when another user owns the document set" in new UserScope {
          val documentSet = factory.documentSet()
          val document = factory.document(documentSetId=documentSet.id)
          val badDocumentSet = factory.documentSet()
          val badDocument = factory.document(documentSetId=badDocumentSet.id)
          factory.documentSetUser(documentSet.id, goodUser.email)
          factory.documentSetUser(badDocumentSet.id, badUser.email)
          auth.userOwningDocument(document.id)(badUser) must beFalse
        }
      }

      "userOwningJob" should {
        "return true when the user owns the job" in new UserScope {
          val documentSet = factory.documentSet()
          val job = factory.documentSetCreationJob(documentSetId=documentSet.id)
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningJob(job.id)(goodUser) must beTrue
        }

        "return false when another user owns the job" in new UserScope {
          val documentSet = factory.documentSet()
          val job = factory.documentSetCreationJob(documentSetId=documentSet.id)
          factory.documentSetUser(documentSet.id, goodUser.email)
          auth.userOwningJob(job.id)(badUser) must beFalse
        }

        "return false when the user owns a different job" in new UserScope {
          val documentSet = factory.documentSet()
          val job = factory.documentSetCreationJob(documentSetId=documentSet.id)
          val otherDocumentSet = factory.documentSet()
          val otherJob = factory.documentSetCreationJob(documentSetId=otherDocumentSet.id)
          factory.documentSetUser(documentSet.id, goodUser.email)
          factory.documentSetUser(otherDocumentSet.id, badUser.email)
          auth.userOwningJob(job.id)(badUser) must beFalse
        }
      }
    }

    "with an ApiToken" should {
      trait ApiTokenScope extends BaseScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=documentSet.id)
      }

      "anyUser" should {
        "return true" in new ApiTokenScope {
          await(auth.anyUser(apiToken)) must beTrue
        }
      }

      "adminUser" should {
        "return false" in new ApiTokenScope {
          await(auth.adminUser(apiToken)) must beFalse
        }
      }

      "userOwningDocumentSet" should {
        "return true when the user owns the document set" in new ApiTokenScope {
          await(auth.userOwningDocumentSet(documentSet.id)(apiToken)) must beTrue
        }

        "return false when another user owns the document set" in new ApiTokenScope {
          val otherDocumentSet = factory.documentSet()
          await(auth.userOwningDocumentSet(otherDocumentSet.id)(apiToken)) must beFalse
        }
      }

      "userOwningTag" should {
        "return true when the user owns the document set and tag" in new ApiTokenScope {
          val tag = factory.tag(documentSetId=documentSet.id)
          await(auth.userOwningTag(documentSet.id, tag.id)(apiToken)) must beTrue
        }

        "return false when the user owns a tag in a different document set" in new ApiTokenScope {
          val documentSet2 = factory.documentSet()
          val tag = factory.tag(documentSetId=documentSet2.id)
          await(auth.userOwningTag(documentSet.id, tag.id)(apiToken)) must beFalse
        }

        "return false when the user does not own the document set" in new ApiTokenScope {
          val tag = factory.tag(documentSetId=documentSet.id)
          val badDocumentSet = factory.documentSet()
          await(auth.userOwningTag(badDocumentSet.id, tag.id)(apiToken)) must beFalse
        }
      }

      "userOwningTree" should {
        "return false" in new ApiTokenScope {
          val node = factory.node(parentId=None)
          val tree = factory.tree(documentSetId=documentSet.id, rootNodeId=node.id)
          await(auth.userOwningTree(tree.id)(apiToken)) must beFalse
        }
      }

      "userOwningView" should {
        "return true when the View has the same ApiToken" in new ApiTokenScope {
          val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
          await(auth.userOwningView(view.id)(apiToken)) must beTrue
        }

        "return false when the ApiToken points to a different View on the same DocumentSet" in new ApiTokenScope {
          val apiToken2 = factory.apiToken(documentSetId=documentSet.id, token="token2")
          val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
          val view2 = factory.view(documentSetId=documentSet.id, apiToken=apiToken2.token)
          await(auth.userOwningView(view2.id)(apiToken)) must beFalse
        }

        "return false when the view does not exist" in new ApiTokenScope {
          await(auth.userOwningView(1L)(apiToken)) must beFalse
        }
      }

      "userOwningStoreObject" should {
        "return true when the ApiToken owns the store" in new ApiTokenScope {
          val store = factory.store(apiToken=apiToken.token)
          val storeObject = factory.storeObject(storeId=store.id)
          await(auth.userOwningStoreObject(storeObject.id)(apiToken)) must beTrue
        }

        "return false when the ApiToken owns a different store" in new ApiTokenScope {
          val store = factory.store(apiToken=apiToken.token)
          val apiToken2 = factory.apiToken(documentSetId=documentSet.id, token="token2")
          val store2 = factory.store(apiToken=apiToken2.token)
          val storeObject = factory.storeObject(storeId=store2.id)
          await(auth.userOwningStoreObject(storeObject.id)(apiToken)) must beFalse
        }
      }

      "userOwningDocument" should {
        "return true when the user owns the document set" in new ApiTokenScope {
          val document = factory.document(documentSetId=documentSet.id)
          await(auth.userOwningDocument(document.id)(apiToken)) must beTrue
        }

        "return false when another user owns the document set" in new ApiTokenScope {
          val document = factory.document(documentSetId=documentSet.id)
          val documentSet2 = factory.documentSet()
          val apiToken2 = factory.apiToken(documentSetId=documentSet2.id, token="token2")
          val document2 = factory.document(documentSetId=documentSet2.id)
          await(auth.userOwningDocument(document2.id)(apiToken)) must beFalse
        }
      }

      "userOwningJob" should {
        "return false" in new ApiTokenScope {
          val job = factory.documentSetCreationJob(documentSetId=documentSet.id)
          await(auth.userOwningJob(job.id)(apiToken)) must beFalse
        }
      }
    }
  }
}
