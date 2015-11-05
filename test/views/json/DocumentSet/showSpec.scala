package views.json.DocumentSet

import java.util.Date
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

import com.overviewdocs.metadata.{MetadataField,MetadataFieldType,MetadataSchema}
import com.overviewdocs.models.View
import com.overviewdocs.models.{DocumentSet,DocumentSetCreationJob,DocumentSetCreationJobState,DocumentSetCreationJobType,Tag,Tree}
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class showSpec extends views.ViewSpecification {
  trait BaseScope extends JsonViewSpecificationScope {
    val metadataSchema = MetadataSchema(1, Seq(MetadataField("foo", MetadataFieldType.String)))
    val documentSet: DocumentSet = factory.documentSet(metadataSchema=metadataSchema)
    val trees: Iterable[Tree] = Seq()
    val views: Iterable[View] = Seq()
    val viewJobs: Iterable[DocumentSetCreationJob] = Seq()
    val tags: Iterable[Tag] = Seq()
    override def result = show(documentSet, trees, views, viewJobs, tags)
  }

  "Tree view generated Json" should {
    "contain tags" in new BaseScope {
      override val tags = Seq(
        factory.tag(id=5L, name="tag1"),
        factory.tag(id=15L, name="tag2")
      )

      json must /("tags") */("id" -> 5L)
      json must /("tags") */("name" -> "tag1")
      json must /("tags") */("id" -> 15L)
    }

    "show nDocuments" in new BaseScope {
      override val documentSet = factory.documentSet(documentCount=10)
      json must /("nDocuments" -> 10L)
    }

    "contain trees" in new BaseScope {
      override val trees = Seq(factory.tree(
        documentSetId=10L,
        id=2L,
        rootNodeId=3L,
        title="title",
        jobId=4L,
        documentCount=100,
        createdAt=new java.sql.Timestamp(1000),
        lang="en"
      ))

      json must /("views") */("type" -> "tree")
      json must /("views") */("id" -> 2L)
      json must /("views") */("title" -> "title")
      json must /("views") */("jobId" -> 4L)
      json must /("views") */("createdAt" -> "1970-01-01T00:00:01Z")
      json must /("views") */("creationData") /#(3) /#(0) / "lang"
      json must /("views") */("creationData") /#(3) /#(1) / "en"
      json must /("views") */("nDocuments" -> 100)
    }

    "contain views" in new BaseScope {
      override val views = Seq(factory.view(
        id=1L,
        title="foo",
        createdAt=new java.sql.Timestamp(1000),
        url="http://localhost:9001",
        apiToken="api-token"
      ))

      json must /("views") */("type" -> "view")
      json must /("views") */("id" -> 1L)
      json must /("views") */("title" -> "foo")
      json must /("views") */("createdAt" -> "1970-01-01T00:00:01Z")
      json must /("views") */("url" -> "http://localhost:9001")
      json must /("views") */("apiToken" -> "api-token")
    }

    "contain view jobs" in new BaseScope {
      override val viewJobs = Seq(factory.documentSetCreationJob(
        id=2L,
        documentSetId=1L,
        treeTitle=Some("tree job"),
        jobType=DocumentSetCreationJobType.Recluster,
        state=DocumentSetCreationJobState.InProgress
      ))

      json must /("views") /#(0) /("id" -> 2.0)
      json must /("views") /#(0) /("type" -> "job")
      // For the rest, we assume the call to views.json.Views.index() is successful.
    }

    "contain metadataSchema" in new BaseScope {
      json must /("metadataSchema") /("version" -> 1)
      json must /("metadataSchema") /("fields") /#(0) /("name" -> "foo")
      json must /("metadataSchema") /("fields") /#(0) /("type" -> "String")
    }
  }
}
