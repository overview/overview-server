package views.html.DocumentSet

import org.overviewproject.tree.orm.{DocumentSet,DocumentSetCreationJob}
import org.overviewproject.tree.orm.finders.ResultPage

class indexSpec extends views.html.ViewSpecification {
  trait BaseScope extends HtmlViewSpecificationScope {
    def jobs : Seq[(DocumentSetCreationJob,DocumentSet,Long)] = Seq()
    def documentSets: Seq[(DocumentSet,Long)] = Seq()
    def documentSetsPage = ResultPage(documentSets, 10, 1)

    def result = index(fakeUser, documentSetsPage, jobs)
  }

  "DocumentSet.index" should {
    "not show DocumentSets if there are none" in new BaseScope {
      $(".document-sets").length must beEqualTo(0)
    }

    "show DocumentSets if there are some" in new BaseScope {
      override def documentSets = Seq((DocumentSet(), 1))
      $(".document-sets").length must beEqualTo(1)
    }

    "not show Jobs if there are none" in new BaseScope {
      $(".document-set-creation-jobs").length must beEqualTo(0)
    }

    "render DocumentSets if there are some" in new BaseScope {
      override def documentSets = Seq(
        (DocumentSet(id=1, title="title1", query=Some("query1")), 1),
        (DocumentSet(id=2, title="title2", query=Some("query2")), 1)
      )
      $(".document-sets").length must equalTo(1)
      $(".document-sets li[data-document-set-id='1']").length must beEqualTo(1)
      $(".document-sets li[data-document-set-id='2']").length must beEqualTo(1)
    }
    
    "define error-list popup if there are DocumentSets" in new BaseScope {
      override def documentSets = Seq(
        (DocumentSet(id=1, title="title1", query=Some("query1")), 1),
        (DocumentSet(id=2, title="title2", query=Some("query2")), 1)
      )
      $("#error-list-modal").length must beEqualTo(1)
    }
  }
}
