package views.json.Node

import java.util.Date
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

import org.overviewproject.models.Viz
import org.overviewproject.tree.orm.{DocumentSetCreationJob, DocumentSetCreationJobState, Node, Document, Tag}
import org.overviewproject.tree.DocumentSetCreationJobType

class indexSpec extends Specification with JsonMatchers {
  private def buildNode(id: Long, parentId: Option[Long], cachedSize: Int, cachedDocumentIds: Array[Long]) : Node = {
    Node(
      id=id,
      treeId = 1L,
      parentId=parentId,
      description="description",
      cachedSize=cachedSize,
      cachedDocumentIds=cachedDocumentIds,
      isLeaf=false
    )
  }

  private def buildViz(aId: Long, aTitle: String, aCreatedAt: Date, aCreationData: Seq[(String,String)]) : Viz = {
    new Viz {
      override val id = aId
      override val title = aTitle
      override val createdAt = aCreatedAt
      override def creationData = aCreationData
    }
  }

  "Tree view generated Json" should {
    
    "contain all nodes" in {
      val nodes = List(
        buildNode(1, None, 2, Array(1L, 2L)),
        buildNode(2, Some(1L), 1, Array(1L)),
        buildNode(3, Some(1L), 1, Array(2L))
      )

      val treeJson = index(Seq(), Seq(), nodes, Seq(), Seq()).toString
      
      treeJson must /("nodes") */("id" -> 1)
      treeJson must /("nodes") */("id" -> 2)
      treeJson must /("nodes") */("id" -> 3)
    }

    "contain tags" in {
      val nodes = Seq[Node]()

      val baseTag = Tag(id=5L, name="tag1", documentSetId=1L, color="ffffff")

      val tags = Seq[Tag](
        baseTag.copy(id=5L, name="tag1"),
        baseTag.copy(id=15L, name="tag2")
      )
      val treeJson = index(Seq(), Seq(), nodes, tags, Seq()).toString
      
      treeJson must /("tags") */("id" -> 5L)
      treeJson must /("tags") */("name" -> "tag1")
      treeJson must /("tags") */("id" -> 15L)
    }

    "contain vizs" in {
      val viz = buildViz(2L, "title", new Date(1000), Seq("foo" -> "bar"))

      val json = index(Seq(viz), Seq(), Seq(), Seq(), Seq()).toString

      json must /("vizs") */("type" -> "viz")
      json must /("vizs") */("id" -> 2L)
      json must /("vizs") */("title" -> "title")
      json must /("vizs") */("createdAt" -> "1970-01-01T00:00:01Z")
      json must /("vizs") */("creationData") /#(0) /#(0) / "foo"
      json must /("vizs") */("creationData") /#(0) /#(1) / "bar"
    }

    "contain viz jobs" in {
      val vizJob = DocumentSetCreationJob(
        id=2L,
        documentSetId=1L,
        treeTitle=Some("tree job"),
        jobType=DocumentSetCreationJobType.Recluster,
        state=DocumentSetCreationJobState.InProgress
      )

      val json = index(Seq(), Seq(vizJob), Seq(), Seq(), Seq()).toString

      json must /("vizs") /#(0) /("id" -> 2.0)
      json must /("vizs") /#(0) /("type" -> "job")
      // For the rest, we assume the call to views.json.Vizs.index() is successful.
    }
  }
}
