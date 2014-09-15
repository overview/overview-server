package views.json.DocumentSet

import java.util.Date
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

import org.overviewproject.models.Viz
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

      json must /("vizs") */("type" -> "tree")
      json must /("vizs") */("id" -> 2L)
      json must /("vizs") */("title" -> "title")
      json must /("vizs") */("jobId" -> 4L)
      json must /("vizs") */("createdAt" -> "1970-01-01T00:00:01Z")
      json must /("vizs") */("creationData") /#(3) /#(0) / "lang"
      json must /("vizs") */("creationData") /#(3) /#(1) / "en"
      json must /("vizs") */("nDocuments" -> 100)
    }

    "contain vizs" in {
      val viz = PodoFactory.viz(
        id=1L,
        title="foo",
        createdAt=new java.sql.Timestamp(1000),
        url="http://localhost:9001",
        apiToken="api-token"
      )

      val json: String = show(buildDocumentSet(10), Seq(), Seq(viz), Seq(), Seq(), Seq()).toString

      json must /("vizs") */("type" -> "viz")
      json must /("vizs") */("id" -> 1L)
      json must /("vizs") */("title" -> "foo")
      json must /("vizs") */("createdAt" -> "1970-01-01T00:00:01Z")
      json must /("vizs") */("url" -> "http://localhost:9001")
      json must /("vizs") */("apiToken" -> "api-token")
    }

    "contain viz jobs" in {
      val vizJob = DocumentSetCreationJob(
        id=2L,
        documentSetId=1L,
        treeTitle=Some("tree job"),
        jobType=DocumentSetCreationJobType.Recluster,
        state=DocumentSetCreationJobState.InProgress
      )

      val json = show(buildDocumentSet(10), Seq(), Seq(), Seq(vizJob), Seq(), Seq()).toString

      json must /("vizs") /#(0) /("id" -> 2.0)
      json must /("vizs") /#(0) /("type" -> "job")
      // For the rest, we assume the call to views.json.Vizs.index() is successful.
    }
  }
}
