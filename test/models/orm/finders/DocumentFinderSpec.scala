package models.orm.finders

import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

import helpers.DbTestContext
import models.orm.{ TestSchema }
import models.Selection
import org.overviewproject.tree.orm._

class DocumentFinderSpec extends Specification {
  step(start(FakeApplication()))

  "DocumentFinder" should {
    "find untagged documents" in new DbTestContext {
      import org.overviewproject.postgres.SquerylEntrypoint._

      val documentSet = TestSchema.documentSets.insertOrUpdate(DocumentSet())
      val documents = Seq.tabulate(10)(n => TestSchema.documents.insert(Document(id=n, documentSetId=documentSet.id)))
      val rootNodeId = 3L
      val node = TestSchema.nodes.insert(Node(
        id=rootNodeId,
        rootId=rootNodeId,
        parentId=None,
        description="root",
        cachedSize=documents.length,
        isLeaf=true
      ))
      val tree = TestSchema.trees.insert(Tree(
        id=0L,
        documentSetId=documentSet.id,
        rootNodeId=rootNodeId,
        jobId=0L,
        title="tree",
        documentCount=documents.length,
        lang="en"
      ))
      val tag = TestSchema.tags.insertOrUpdate(Tag(
        documentSetId=documentSet.id,
        name="tag",
        color="000000"
      ))

      val taggedDocuments = documents.take(5)
      val untaggedDocuments = documents.takeRight(5)

      documents.foreach { d =>
        TestSchema.nodeDocuments.insertOrUpdate(NodeDocument(nodeId=node.id, documentId=d.id))
      }

      taggedDocuments.foreach { d =>
        TestSchema.documentTags.insertOrUpdate(DocumentTag(documentId=d.id, tagId=tag.id))
      }

      val selection = Selection(documentSet.id, Nil, Nil, Nil, Nil, true)

      val result = DocumentFinder.bySelection(selection).toSeq

      result.map(_.id).toSet must beEqualTo(untaggedDocuments.map(_.id).toSet)
    }
  }

  step(stop)
}
