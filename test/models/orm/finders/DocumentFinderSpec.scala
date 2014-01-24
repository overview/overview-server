package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import helpers.DbTestContext
import org.overviewproject.tree.orm._
import models.orm.{ TestSchema }
import org.overviewproject.tree.orm.NodeDocument
import models.Selection

class DocumentFinderSpec extends Specification {

  step(start(FakeApplication()))



  "DocumentFinder" should {

    trait TaggedDocumentsContext extends DbTestContext {
      var documentSet: DocumentSet = _
      var documents: Seq[Document] = _
      var untaggedDocumentIds: Seq[Int] = _

      override def setupWithDb = {
        documentSet = TestSchema.documentSets.insertOrUpdate(DocumentSet())

        Seq.tabulate(10)(n => TestSchema.documents.insert(Document(id = n, documentSetId = documentSet.id)))
        TestSchema.trees.insert(Tree(1, documentSet.id,"tree", 100, "en", "", "" ))
        TestSchema.nodes.insert(
          Node(id = 1,
            treeId = 1,
            documentSetId = documentSet.id,
            parentId = None,
            description = "",
            cachedSize = 0,
            cachedDocumentIds = Array.empty,
            isLeaf = true))
        val tag = TestSchema.tags.insertOrUpdate(Tag(documentSetId = documentSet.id, name = "tag", color = "000000"))
        val taggedIds = 0 to 4
        val unTaggedIds = 5 to 9

        TestSchema.documentTags.insert(taggedIds.map(id => DocumentTag(id, tag.id)))
        TestSchema.nodeDocuments.insert((taggedIds ++ unTaggedIds.take(3)).map(id =>
          NodeDocument(1, id)))

        untaggedDocumentIds = unTaggedIds.take(3)
      }
    }

    "find untagged documents in nodes" in new TaggedDocumentsContext {
      val untagged = DocumentFinder.bySelection(Selection(documentSet.id, Nil, Nil, Nil, Nil, true)).toSeq
      
      untagged.map(_.id) must haveTheSameElementsAs(untaggedDocumentIds)
    }
  }
  step(stop)
}