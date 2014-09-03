package views.html.DocumentSet

import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob, Tree }
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.tree.orm.finders.ResultPage

import models.OverviewUser

class indexSpec extends views.html.ViewSpecification {
  trait BaseScope extends HtmlViewSpecificationScope {
    def jobs : Seq[(DocumentSetCreationJob,DocumentSet,Long)] = Seq()
    def documentSets: Seq[(DocumentSet, Int,Int)] = Seq()
    def documentSetsPage = ResultPage(documentSets, 10, 1)

    def result = index(fakeUser, documentSetsPage, jobs)
  }

  trait BaseScopeWithJob extends BaseScope {
    val job = mock[DocumentSetCreationJob]
    job.state returns InProgress
    job.statusDescription returns "someKey"
    val documentSet = mock[DocumentSet]
    documentSet.id returns 1L
    documentSet.title returns "Title"
    documentSet.query returns Some("query")

    override val jobs = Seq((job, documentSet, 0L))
  }

  "DocumentSet.index" should {
    "Not show DocumentSets if there are none" in new BaseScope {
      $(".document-sets").length must beEqualTo(0)
    }

    "Show DocumentSets if there are some" in new BaseScope {
      override def documentSets = Seq((DocumentSet(), 1, 0))
      $(".document-sets").length must beEqualTo(1)
    }

    "Not show Jobs if there are none" in new BaseScope {
      $(".document-set-creation-jobs").length must beEqualTo(0)
    }

    "Show Jobs if there are some" in new BaseScopeWithJob {
      $(".document-set-creation-jobs").length must beEqualTo(1)
    }

    "Show DocumentSets UL if there are none, but there are Jobs" in new BaseScopeWithJob {
      $(".document-sets ul").length must equalTo(1)
    }

    "render DocumentSets if there are some" in new BaseScope {
      override def documentSets = Seq(
        (DocumentSet(id=1, title="title1", query=Some("query1")), 1, 0),
        (DocumentSet(id=2, title="title2", query=Some("query2")), 1, 0)
      )
      $(".document-sets").length must equalTo(1)
      $(".document-sets li[data-document-set-id='1']").length must beEqualTo(1)
      $(".document-sets li[data-document-set-id='2']").length must beEqualTo(1)
    }
    
    "Define error-list popup if there are DocumentSets" in new BaseScope {
      override def documentSets = Seq(
        (DocumentSet(id=1, title="title1", query=Some("query1")), 1, 0),
        (DocumentSet(id=2, title="title2", query=Some("query2")), 1, 0)
      )
      $("#error-list-modal").length must beEqualTo(1)
    }

    "Define error-list popup if there are jobs" in new BaseScopeWithJob {
      $("#error-list-modal").length must beEqualTo(1)
    }
  }
}
