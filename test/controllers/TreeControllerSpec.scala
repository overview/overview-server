package controllers

import play.api.mvc.Flash
import org.specs2.specification.Scope
import org.specs2.matcher.Matcher

import org.overviewproject.tree.orm.{DocumentSet, DocumentSetCreationJob, Tag, Tree}

class TreeControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockStorage = mock[TreeController.Storage]

    val controller = new TreeController {
      override val storage = mockStorage
    }
  }

  "TreeController.create" should {
    trait CreateScope extends BaseScope {
      val documentSetId = 1L
      def formBody : Seq[(String,String)] = Seq("tree_title" -> "tree title", "lang" -> "en", "supplied_stop_words" -> "", "important_words" -> "")
      def request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
      def create(documentSetId: Long) = controller.create(documentSetId)(request)
      def result = create(documentSetId)
    }

    "store a DocumentSetCreationJob" in new CreateScope {
      h.status(result) // store result
      def beJobWithDocumentSetId(id: Long) : Matcher[DocumentSetCreationJob] = beLike{ case (j: DocumentSetCreationJob) => j.documentSetId must beEqualTo(id) }
      there was one(mockStorage).insertJob(argThat(beJobWithDocumentSetId(documentSetId)))
    }

    "return a BAD_REQUEST if the form is filled in badly" in new CreateScope {
      override def formBody = Seq()
      h.status(result) must beEqualTo(h.BAD_REQUEST)
    }

    trait CreateScopeWithTag extends CreateScope {
      def tagId: Option[Long]
      override def formBody = super.formBody ++ Seq("tag_id" -> tagId.map(_.toString).getOrElse(""))
      def beJobWithTreeData(expected: Option[(Long,String)]) = beLike[DocumentSetCreationJob] { case (j: DocumentSetCreationJob) =>
        j.tagId must beEqualTo(expected.map(_._1))
        j.treeDescription must beEqualTo(expected.map(_._2))
      }
      // Call like this: `testTree(Some((102L, "description")))`
      def testTree(expected: Option[(Long,String)]) = {
        h.status(result) // store result
        there was one(mockStorage).insertJob(argThat(beJobWithTreeData(expected)))
      }
    }

    "store no description when there is no tag" in new CreateScopeWithTag {
      override def tagId = None
      testTree(None)
    }

    "store a description when there is a tag" in new CreateScopeWithTag {
      override def tagId = Some(102L)
      val mockTag = mock[Tag]
      mockTag.name returns "tag name"
      mockStorage.findTag(documentSetId, 102L) returns Some(mockTag)
      testTree(Some((102L, "documents with tag “tag name”")))
    }

    "return NotFound when the tag is not in the document set" in new CreateScopeWithTag {
      override def tagId = Some(1023L)
      mockStorage.findTag(documentSetId, 1023L) returns None
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }
  }
}
