package controllers.auth

import slick.jdbc.JdbcBackend.Session

import models.User
import org.overviewproject.models.{DocumentSetUser,UserRole}
import org.overviewproject.test.DbSpecification

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
  trait BaseScope extends DbScope {
    val auth = Authorities
  }

  "with a User" should {
    trait UserScope extends BaseScope {
      val goodUser = User(email="good@example.org")
      val badUser = User(email="bad@example.org")
    }

    "anyUser" should {
      "return true" in new UserScope {
        await(auth.anyUser(goodUser)) must beTrue
      }
    }

    "adminUser" should {
      "return true for an admin" in new UserScope {
        await(auth.adminUser(User(role=UserRole.Administrator))) must beTrue
      }

      "return false for a non-admin" in new UserScope {
        await(auth.adminUser(User(role=UserRole.NormalUser))) must beFalse
      }
    }

    "apiDocumentSetCreator" should {
      "return false" in new UserScope {
        await(auth.apiDocumentSetCreator(User(role=UserRole.NormalUser))) must beFalse
      }
    }

    "userOwningDocumentSet" should {
      "return true when the user owns the document set" in new UserScope {
        val documentSet = factory.documentSet()
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningDocumentSet(documentSet.id)(goodUser)) must beTrue
      }

      "return false when another user owns the document set" in new UserScope {
        val documentSet = factory.documentSet()
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningDocumentSet(documentSet.id)(badUser)) must beFalse
      }

      "return false when the user owns another document set" in new UserScope {
        val documentSet = factory.documentSet()
        val otherDocumentSet = factory.documentSet()
        factory.documentSetUser(otherDocumentSet.id, goodUser.email)
        await(auth.userOwningDocumentSet(documentSet.id)(badUser)) must beFalse
      }
    }

    "userViewingDocumentSet" should {
      "return true when the user owns the document set" in new UserScope {
        val documentSet = factory.documentSet()
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userViewingDocumentSet(documentSet.id)(goodUser)) must beTrue
      }

      "return true then the user views the document set" in new UserScope {
        val documentSet = factory.documentSet()
        factory.documentSetUser(documentSet.id, goodUser.email, new DocumentSetUser.Role(false))
        await(auth.userViewingDocumentSet(documentSet.id)(goodUser)) must beTrue
      }

      "return true when the document set is public" in new UserScope {
        val documentSet = factory.documentSet(isPublic=true)
        await(auth.userViewingDocumentSet(documentSet.id)(goodUser)) must beTrue
      }

      "return false when another document set is public" in new UserScope {
        val documentSet = factory.documentSet(isPublic=false)
        val otherDocumentSet = factory.documentSet(isPublic=true)
        await(auth.userViewingDocumentSet(documentSet.id)(goodUser)) must beFalse
      }

      "return false when another user owns the document set" in new UserScope {
        val documentSet = factory.documentSet()
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userViewingDocumentSet(documentSet.id)(badUser)) must beFalse
      }

      "return false when the user owns another document set" in new UserScope {
        val documentSet = factory.documentSet()
        val otherDocumentSet = factory.documentSet()
        factory.documentSetUser(otherDocumentSet.id, goodUser.email)
        await(auth.userViewingDocumentSet(documentSet.id)(badUser)) must beFalse
      }
    }

    "userOwningTag" should {
      "return true when the user owns the document set and tag" in new UserScope {
        val documentSet = factory.documentSet()
        val tag = factory.tag(documentSetId=documentSet.id)
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningTag(documentSet.id, tag.id)(goodUser)) must beTrue
      }

      "return false then the user owns the tag in a different document set" in new UserScope {
        val documentSet = factory.documentSet()
        val otherDocumentSet = factory.documentSet()
        val tag = factory.tag(documentSetId=otherDocumentSet.id)
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningTag(documentSet.id, tag.id)(goodUser)) must beFalse
      }

      "return false when another user owns the document set" in new UserScope {
        val documentSet = factory.documentSet()
        val tag = factory.tag(documentSetId=documentSet.id)
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningTag(documentSet.id, tag.id)(badUser)) must beFalse
      }
    }

    "userOwningTree" should {
      "return true when the user owns the document set and tree" in new UserScope {
        val documentSet = factory.documentSet()
        val node = factory.node(parentId=None)
        val tree = factory.tree(documentSetId=documentSet.id, rootNodeId=node.id)
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningTree(tree.id)(goodUser)) must beTrue
      }

      "return false when the user owns a tree in a different document set" in new UserScope {
        val documentSet = factory.documentSet()
        val otherDocumentSet = factory.documentSet()
        val node = factory.node(parentId=None)
        val tree = factory.tree(documentSetId=otherDocumentSet.id, rootNodeId=node.id)
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningTree(tree.id)(goodUser)) must beFalse
      }

      "return false when the tree does not exist" in new UserScope {
        val documentSet = factory.documentSet()
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningTree(1L)(goodUser)) must beFalse
      }

      "return false when a different user owns the tree" in new UserScope {
        val documentSet = factory.documentSet()
        val node = factory.node(parentId=None)
        val tree = factory.tree(documentSetId=documentSet.id, rootNodeId=node.id)
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningTree(tree.id)(badUser)) must beFalse
      }
    }

    "userOwningView" should {
      "return true when the user owns the document set and view" in new UserScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningView(view.id)(goodUser)) must beTrue
      }

      "return false when the user owns a view in a different document set" in new UserScope {
        val documentSet = factory.documentSet()
        val otherDocumentSet = factory.documentSet()
        val otherApiToken = factory.apiToken(documentSetId=Some(otherDocumentSet.id))
        val view = factory.view(documentSetId=otherDocumentSet.id, apiToken=otherApiToken.token)
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningView(view.id)(goodUser)) must beFalse
      }

      "return false when the view does not exist" in new UserScope {
        val documentSet = factory.documentSet()
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningView(1L)(goodUser)) must beFalse
      }

      "return false when a different user owns the view" in new UserScope {
        val documentSet = factory.documentSet()
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningView(view.id)(badUser)) must beFalse
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
        await(auth.userOwningDocument(document.id)(goodUser)) must beTrue
      }

      "return false when another user owns the document set" in new UserScope {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id)
        val badDocumentSet = factory.documentSet()
        val badDocument = factory.document(documentSetId=badDocumentSet.id)
        factory.documentSetUser(documentSet.id, goodUser.email)
        factory.documentSetUser(badDocumentSet.id, badUser.email)
        await(auth.userOwningDocument(document.id)(badUser)) must beFalse
      }
    }

    "userOwningJob" should {
      "return true when the user owns the job" in new UserScope {
        val documentSet = factory.documentSet()
        val job = factory.documentSetCreationJob(documentSetId=documentSet.id)
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningJob(job.id)(goodUser)) must beTrue
      }

      "return false when another user owns the job" in new UserScope {
        val documentSet = factory.documentSet()
        val job = factory.documentSetCreationJob(documentSetId=documentSet.id)
        factory.documentSetUser(documentSet.id, goodUser.email)
        await(auth.userOwningJob(job.id)(badUser)) must beFalse
      }

      "return false when the user owns a different job" in new UserScope {
        val documentSet = factory.documentSet()
        val job = factory.documentSetCreationJob(documentSetId=documentSet.id)
        val otherDocumentSet = factory.documentSet()
        val otherJob = factory.documentSetCreationJob(documentSetId=otherDocumentSet.id)
        factory.documentSetUser(documentSet.id, goodUser.email)
        factory.documentSetUser(otherDocumentSet.id, badUser.email)
        await(auth.userOwningJob(job.id)(badUser)) must beFalse
      }
    }
  }

  "with a DocumentSet ApiToken" should {
    trait ApiTokenScope extends BaseScope {
      val documentSet = factory.documentSet()
      val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
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

    "apiDocumentSetCreator" should {
      "return false" in new ApiTokenScope {
        await(auth.apiDocumentSetCreator(apiToken)) must beFalse
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

    "userViewingDocumentSet" should {
      "return true when the user owns the document set" in new ApiTokenScope {
        await(auth.userViewingDocumentSet(documentSet.id)(apiToken)) must beTrue
      }

      "return false when another user owns the document set" in new ApiTokenScope {
        val otherDocumentSet = factory.documentSet()
        await(auth.userViewingDocumentSet(otherDocumentSet.id)(apiToken)) must beFalse
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
        val apiToken2 = factory.apiToken(documentSetId=Some(documentSet.id), token="token2")
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
        val apiToken2 = factory.apiToken(documentSetId=Some(documentSet.id), token="token2")
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
        val apiToken2 = factory.apiToken(documentSetId=Some(documentSet2.id), token="token2")
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

  "with a create-docset ApiToken" should {
    trait ApiTokenScope extends BaseScope {
      val apiToken = factory.apiToken(documentSetId=None)
    }

    "adminUser" should {
      "return false" in new ApiTokenScope {
        await(auth.adminUser(apiToken)) must beFalse
      }
    }

    "apiDocumentSetCreator" should {
      "return true" in new ApiTokenScope {
        await(auth.apiDocumentSetCreator(apiToken)) must beTrue
      }
    }

    "userOwningDocumentSet" should {
      "return false" in new ApiTokenScope {
        val documentSet = factory.documentSet()
        await(auth.userOwningDocumentSet(documentSet.id)(apiToken)) must beFalse
      }
    }

    "userViewingDocumentSet" should {
      "return false" in new ApiTokenScope {
        val documentSet = factory.documentSet()
        await(auth.userViewingDocumentSet(documentSet.id)(apiToken)) must beFalse
      }
    }
  }
}
