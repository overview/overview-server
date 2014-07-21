package views.json.DocumentSet

import java.util.Date
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

import org.overviewproject.models.Viz
import org.overviewproject.tree.orm.{DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, Tag}
import org.overviewproject.tree.DocumentSetCreationJobType

class showSpec extends Specification with JsonMatchers {
  private def buildViz(aId: Long, aTitle: String, aJobId: Long, aNDocuments: Int, aCreatedAt: Date, aCreationData: Seq[(String,String)]) : Viz = {
    new Viz {
      override val id = aId
      override val title = aTitle
      override val jobId = aJobId
      override val createdAt = aCreatedAt
      override def creationData = aCreationData
      override val documentCount = aNDocuments
    }
  }

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
      val treeJson = show(buildDocumentSet(10), Seq(), Seq(), tags, Seq()).toString
      
      treeJson must /("tags") */("id" -> 5L)
      treeJson must /("tags") */("name" -> "tag1")
      treeJson must /("tags") */("id" -> 15L)
    }

    "show nDocuments" in {
      val json = show(buildDocumentSet(10), Seq(), Seq(), Seq(), Seq()).toString
      json must /("nDocuments" -> 10L)
    }

    "contain vizs" in {
      val viz = buildViz(2L, "title", 4L, 100, new Date(1000), Seq("foo" -> "bar"))

      val json = show(buildDocumentSet(10), Seq(viz), Seq(), Seq(), Seq()).toString

      json must /("vizs") */("type" -> "viz")
      json must /("vizs") */("id" -> 2L)
      json must /("vizs") */("title" -> "title")
      json must /("vizs") */("jobId" -> 4L)
      json must /("vizs") */("createdAt" -> "1970-01-01T00:00:01Z")
      json must /("vizs") */("creationData") /#(0) /#(0) / "foo"
      json must /("vizs") */("creationData") /#(0) /#(1) / "bar"
      json must /("vizs") */("nDocuments" -> 100)
    }

    "contain viz jobs" in {
      val vizJob = DocumentSetCreationJob(
        id=2L,
        documentSetId=1L,
        treeTitle=Some("tree job"),
        jobType=DocumentSetCreationJobType.Recluster,
        state=DocumentSetCreationJobState.InProgress
      )

      val json = show(buildDocumentSet(10), Seq(), Seq(vizJob), Seq(), Seq()).toString

      json must /("vizs") /#(0) /("id" -> 2.0)
      json must /("vizs") /#(0) /("type" -> "job")
      // For the rest, we assume the call to views.json.Vizs.index() is successful.
    }
  }
}
