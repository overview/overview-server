package views.html.DocumentSet

import org.overviewproject.tree.orm.{DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, Tree}
import org.overviewproject.tree.DocumentSetCreationJobType
import models.OverviewUser

class _documentSetSpec extends views.html.ViewSpecification {
  trait BaseScope extends HtmlViewSpecificationScope {
    def documentSet: DocumentSet = DocumentSet(id=1L)
    def trees: Seq[Tree] = Seq()
    def treeErrorJobs: Seq[DocumentSetCreationJob] = Seq()

    def result = _documentSet(documentSet, trees, treeErrorJobs, fakeUser)

    def fakeTree(documentSetId: Long, id: Long) = Tree(
      id=id,
      documentSetId=documentSetId,
      title="title",
      documentCount=10,
      lang="en"
    )

    def fakeTreeErrorJob(documentSetId: Long, id: Long) = DocumentSetCreationJob(
      id=id,
      documentSetId=documentSetId,
      treeTitle=Some("tree title"),
      state=DocumentSetCreationJobState.Error,
      jobType=DocumentSetCreationJobType.Recluster
    )
  }

  trait DocumentSetWithErrorsContext extends BaseScope {
    val numberOfErrors = 10
    override def documentSet = DocumentSet(1L, documentProcessingErrorCount = numberOfErrors)
  }

  "DocumentSet._documentSet" should {
    "be an <li>" in new BaseScope {
      html.body must beMatching("""(?s)\A\s*<li.*</li>\s*\z$""".r)
    }

    "should have a data-document-set-id attribute" in new BaseScope {
      $("li:first").attr("data-document-set-id") must equalTo(documentSet.id.toString)
    }

    "should link to the Tree from the h3 if there is one tree" in new BaseScope {
      override def trees = Seq(fakeTree(documentSet.id, 10))

      val href = $("h3 a[href]").get().headOption.map(_.getAttribute("href"))
      href must beSome(contain(s"/${documentSet.id}/trees/10"))
    }

    "should not link to the Tree from the h3 if there are several" in new BaseScope {
      override def trees = Seq(fakeTree(documentSet.id, 10), fakeTree(documentSet.id, 11))
      $("h3 a[href]").length must beEqualTo(0)
    }

    "should set div.trees.single if there is one tree" in new BaseScope {
      override def trees = Seq(fakeTree(documentSet.id, 10))
      $("div.trees.single").length must beEqualTo(1)
    }

    "should not set div.trees.single if there are many trees" in new BaseScope {
      override def trees = Seq(fakeTree(documentSet.id, 10), fakeTree(documentSet.id, 11))
      $("div.trees").length must beEqualTo(1)
      $("div.trees.single").length must beEqualTo(0)
    }

    "should not set div.trees.single if there are error jobs" in new BaseScope {
      override def trees = Seq(fakeTree(documentSet.id, 10))
      override def treeErrorJobs = Seq(fakeTreeErrorJob(documentSet.id, 11))
      $("div.trees").length must beEqualTo(1)
      $("div.trees.single").length must beEqualTo(0)
    }

    "should link to each tree in div.trees" in new BaseScope {
      override def trees = Seq(fakeTree(documentSet.id, 10), fakeTree(documentSet.id, 11))
      $("div.trees li[data-tree-id='10'] a[href]").attr("href") must beEqualTo("/documentsets/1/trees/10")
      $("div.trees li[data-tree-id='11'] a[href]").attr("href") must beEqualTo("/documentsets/1/trees/11")
    }

    "should show each error job in div.trees" in new BaseScope {
      override def treeErrorJobs = Seq(fakeTreeErrorJob(documentSet.id, 11), fakeTreeErrorJob(documentSet.id, 12))
      $("div.trees li.error[data-job-id='11']").text() must contain("tree title")
      $("div.trees li.error[data-job-id='12']").text() must contain("tree title")
    }

    "should include a delete button" in new BaseScope {
      $("form.delete").length must be_>=(1)
    }

    "should show a document count" in new BaseScope {
      $("span.document-count").text() must equalTo("no documents")
    }

    "should not show error count if none exist" in new BaseScope {
      $(".error-count").length must be_==(0)
    }

    "should show error count popup if there are errors" in new DocumentSetWithErrorsContext {
      $("a.error-count").text.trim must equalTo("10 documents could not be loaded")
      $("a.error-count").attr("href") must be equalTo("/documentsets/1/error-list")
      $("a.error-count").attr("data-target") must be equalTo("#error-list-modal")
    }
  }
}
