package views.json.DocumentSet

import java.util.Date
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

import org.overviewproject.models.View
import org.overviewproject.models.{DocumentSetCreationJobState, DocumentSetCreationJobType, Tag}
import org.overviewproject.test.factories.{PodoFactory=>factory}

class showSpec extends Specification with JsonMatchers {
  "Tree view generated Json" should {
    "contain tags" in {
      val baseTag = factory.tag(id=5L, name="tag1", documentSetId=1L, color="ffffff")

      val tags = Seq[Tag](
        baseTag.copy(id=5L, name="tag1"),
        baseTag.copy(id=15L, name="tag2")
      )
      val treeJson = show(factory.documentSet(documentCount=10), Seq(), Seq(), Seq(), tags.map(_.toDeprecatedTag)).toString
      
      treeJson must /("tags") */("id" -> 5L)
      treeJson must /("tags") */("name" -> "tag1")
      treeJson must /("tags") */("id" -> 15L)
    }

    "show nDocuments" in {
      val json = show(factory.documentSet(documentCount=10), Seq(), Seq(), Seq(), Seq()).toString
      json must /("nDocuments" -> 10L)
    }

    "contain trees" in {
      val tree = factory.tree(
        documentSetId=10L,
        id=2L,
        rootNodeId=3L,
        title="title",
        jobId=4L,
        documentCount=100,
        createdAt=new java.sql.Timestamp(1000),
        lang="en"
      )

      val json = show(factory.documentSet(documentCount=10), Seq(tree.toDeprecatedTree), Seq(), Seq(), Seq()).toString

      json must /("views") */("type" -> "tree")
      json must /("views") */("id" -> 2L)
      json must /("views") */("title" -> "title")
      json must /("views") */("jobId" -> 4L)
      json must /("views") */("createdAt" -> "1970-01-01T00:00:01Z")
      json must /("views") */("creationData") /#(3) /#(0) / "lang"
      json must /("views") */("creationData") /#(3) /#(1) / "en"
      json must /("views") */("nDocuments" -> 100)
    }

    "contain views" in {
      val view = factory.view(
        id=1L,
        title="foo",
        createdAt=new java.sql.Timestamp(1000),
        url="http://localhost:9001",
        apiToken="api-token"
      )

      val json: String = show(factory.documentSet(documentCount=10), Seq(), Seq(view), Seq(), Seq()).toString

      json must /("views") */("type" -> "view")
      json must /("views") */("id" -> 1L)
      json must /("views") */("title" -> "foo")
      json must /("views") */("createdAt" -> "1970-01-01T00:00:01Z")
      json must /("views") */("url" -> "http://localhost:9001")
      json must /("views") */("apiToken" -> "api-token")
    }

    "contain view jobs" in {
      val viewJob = factory.documentSetCreationJob(
        id=2L,
        documentSetId=1L,
        treeTitle=Some("tree job"),
        jobType=DocumentSetCreationJobType.Recluster,
        state=DocumentSetCreationJobState.InProgress
      )

      val json = show(factory.documentSet(documentCount=10), Seq(), Seq(), Seq(viewJob.toDeprecatedDocumentSetCreationJob), Seq()).toString

      json must /("views") /#(0) /("id" -> 2.0)
      json must /("views") /#(0) /("type" -> "job")
      // For the rest, we assume the call to views.json.Views.index() is successful.
    }
  }
}
