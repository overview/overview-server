package views.json.DocumentSet

import java.util.Date
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

import org.overviewproject.models.View
import org.overviewproject.test.factories.PodoFactory
import org.overviewproject.tree.orm.{DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, Tag, Tree}
import org.overviewproject.tree.DocumentSetCreationJobType

class showSpec extends Specification with JsonMatchers {
  private def buildDocumentSet(nDocuments: Int) : DocumentSet = {
    DocumentSet(documentCount=nDocuments)
  }

  "Tree view generated Json" should {
    "contain tags" in {
      val baseTag = Tag(id=5L, name="tag1", documentSetId=1L, color="ffffff")

      val tags = Seq[Tag](
        baseTag.copy(id=5L, name="tag1"),
        baseTag.copy(id=15L, name="tag2")
      )
      val treeJson = show(buildDocumentSet(10), Seq(), Seq(), Seq(), tags, Seq()).toString
      
      treeJson must /("tags") */("id" -> 5L)
      treeJson must /("tags") */("name" -> "tag1")
      treeJson must /("tags") */("id" -> 15L)
    }

    "show nDocuments" in {
      val json = show(buildDocumentSet(10), Seq(), Seq(), Seq(), Seq(), Seq()).toString
      json must /("nDocuments" -> 10L)
    }

    "contain trees" in {
      val tree = new Tree(
        documentSetId=10L,
        id=2L,
        rootNodeId=3L,
        title="title",
        jobId=4L,
        documentCount=100,
        createdAt=new java.sql.Timestamp(1000),
        lang="en"
      )

      val json = show(buildDocumentSet(10), Seq(tree), Seq(), Seq(), Seq(), Seq()).toString

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
      val view = PodoFactory.view(
        id=1L,
        title="foo",
        createdAt=new java.sql.Timestamp(1000),
        url="http://localhost:9001",
        apiToken="api-token"
      )

      val json: String = show(buildDocumentSet(10), Seq(), Seq(view), Seq(), Seq(), Seq()).toString

      json must /("views") */("type" -> "view")
      json must /("views") */("id" -> 1L)
      json must /("views") */("title" -> "foo")
      json must /("views") */("createdAt" -> "1970-01-01T00:00:01Z")
      json must /("views") */("url" -> "http://localhost:9001")
      json must /("views") */("apiToken" -> "api-token")
    }

    "contain view jobs" in {
      val viewJob = DocumentSetCreationJob(
        id=2L,
        documentSetId=1L,
        treeTitle=Some("tree job"),
        jobType=DocumentSetCreationJobType.Recluster,
        state=DocumentSetCreationJobState.InProgress
      )

      val json = show(buildDocumentSet(10), Seq(), Seq(), Seq(viewJob), Seq(), Seq()).toString

      json must /("views") /#(0) /("id" -> 2.0)
      json must /("views") /#(0) /("type" -> "job")
      // For the rest, we assume the call to views.json.Views.index() is successful.
    }
  }
}
