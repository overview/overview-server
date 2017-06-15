package controllers

import org.specs2.specification.Scope
import org.specs2.matcher.{JsonMatchers,Matcher}
import scala.concurrent.Future

import controllers.backend.{TagBackend,TreeBackend}
import com.overviewdocs.models.{Tag,Tree}
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class TreeControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val mockBackend = mock[TreeBackend]
    val mockTagBackend = mock[TagBackend]

    val controller = new TreeController(
      mockBackend,
      mockTagBackend,
      testMessagesApi
    )
  }

  "TreeController#create" should {
    trait CreateScope extends BaseScope {
      val documentSetId = 1L
      def formBody : Seq[(String,String)] = Seq("tree_title" -> "tree title", "lang" -> "en", "supplied_stop_words" -> "", "important_words" -> "")
      def request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
      def create(documentSetId: Long) = controller.create(documentSetId)(request)
      def result = create(documentSetId)
    }

    "store a Tree" in new CreateScope {
      mockBackend.create(any) returns Future.successful(factory.tree())
      def isTheRightTree: Matcher[Tree.CreateAttributes] = beLike {
        case tree: Tree.CreateAttributes => {
          tree.copy(createdAt=new java.sql.Timestamp(0L)) must beEqualTo(Tree.CreateAttributes(
            documentSetId=1L,
            rootNodeId=None,
            title="tree title",
            description="",
            documentCount=None,
            lang="en",
            suppliedStopWords="",
            importantWords="",
            createdAt=new java.sql.Timestamp(0L),
            tagId=None,
            progress=0.0,
            progressDescription=""
          ))
        }
      }

      h.status(result) must beEqualTo(h.CREATED)
      there was one(mockBackend).create(argThat(isTheRightTree))
    }

    "return a BAD_REQUEST if the form is filled in badly" in new CreateScope {
      override def formBody = Seq()
      h.status(result) must beEqualTo(h.BAD_REQUEST)
      there was no(mockBackend).create(any)
    }

    "store a description when there is a tag" in new CreateScope {
      override def formBody = super.formBody ++ Seq("tag_id" -> "2")

      mockTagBackend.show(1L, 2L) returns Future.successful(Some(factory.tag(name="foo")))
      mockBackend.create(any) returns Future.successful(factory.tree())

      def hasDescription = beLike[Tree.CreateAttributes] { case t: Tree.CreateAttributes => {
        t.description must beEqualTo("controllers.TreeController.treeDescription.fromTag,foo")
      }}

      h.status(result) must beEqualTo(h.CREATED)
      there was one(mockBackend).create(argThat(hasDescription))
    }

    "store no description when the tag does not exist" in new CreateScope {
      override def formBody = super.formBody ++ Seq("tag_id" -> "2")

      mockTagBackend.show(1L, 2L) returns Future.successful(None)
      mockBackend.create(any) returns Future.successful(factory.tree())

      def hasDescription = beLike[Tree.CreateAttributes] { case t: Tree.CreateAttributes => {
        t.description must beEqualTo("")
      }}

      h.status(result) must beEqualTo(h.CREATED)
      there was one(mockBackend).create(argThat(hasDescription))
    }
  }

  "TreeController#update" should {
    trait UpdateScope extends BaseScope {
      val documentSetId = 1L
      val treeId = 2L
      mockBackend.update(any, any) returns Future.successful(Some(factory.tree(title="updated")))
      val request = fakeAuthorizedRequest.withFormUrlEncodedBody("title" -> "submitted")
      lazy val result = controller.update(documentSetId, treeId)(request)
    }

    "call TreeBackend#update" in new UpdateScope {
      h.status(result)
      there was one(mockBackend).update(treeId, Tree.UpdateAttributes(title="submitted"))
    }

    "return the updated Tree" in new UpdateScope {
      h.status(result) must beEqualTo(h.OK)
      val json = h.contentAsString(result)

      json must /("title" -> "updated")
    }

    "return NotFound when the Tree is not found" in new UpdateScope {
      mockBackend.update(any, any) returns Future.successful(None)
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }

    "return BadRequest when the input is bad" in new UpdateScope {
      override val request = fakeAuthorizedRequest.withFormUrlEncodedBody("titleblah" -> "submittedblah")
      h.status(result) must beEqualTo(h.BAD_REQUEST)
    }
  }

  "TreeController#destroy" should {
    trait DestroyScope extends BaseScope {
      val documentSetId = 1L
      val treeId = 2L
      mockBackend.destroy(any) returns Future.successful(Unit)
      def result = controller.destroy(documentSetId, treeId)(fakeAuthorizedRequest)
    }

    "return NoContent" in new DestroyScope {
      h.status(result) must beEqualTo(h.NO_CONTENT)
    }

    "destroy a Tree" in new DestroyScope {
      h.status(result)
      there was one(mockBackend).destroy(treeId)
    }
  }
}
