package views.html.DocumentSet

import org.overviewproject.models.{DocumentSet,DocumentSetCreationJob}
import models.pagination.Page

class indexSpec extends views.html.ViewSpecification {
  trait BaseScope extends HtmlViewSpecificationScope {
    def jobs : Seq[(DocumentSetCreationJob,DocumentSet,Int)] = Seq()
    def documentSets: Seq[(DocumentSet,Int)] = Seq()
    def documentSetsPage = Page(documentSets)

    def result = index(fakeUser, documentSetsPage, jobs)
  }

  "DocumentSet.index" should {
    "not show DocumentSets if there are none" in new BaseScope {
      $(".document-sets").length must beEqualTo(0)
    }

    "show DocumentSets if there are some" in new BaseScope {
      override def documentSets = Seq((factory.documentSet(), 1))
      $(".document-sets").length must beEqualTo(1)
    }

    "not show Jobs if there are none" in new BaseScope {
      $(".document-set-creation-jobs").length must beEqualTo(0)
    }

    "render DocumentSets if there are some" in new BaseScope {
      override def documentSets = Seq(
        (factory.documentSet(id=1, title="title1", query=Some("query1")), 1),
        (factory.documentSet(id=2, title="title2", query=Some("query2")), 1)
      )
      $(".document-sets").length must equalTo(1)
      $(".document-sets li[data-document-set-id='1']").length must beEqualTo(1)
      $(".document-sets li[data-document-set-id='2']").length must beEqualTo(1)
    }
    
    "define error-list popup if there are DocumentSets" in new BaseScope {
      override def documentSets = Seq(
        (factory.documentSet(id=1, title="title1", query=Some("query1")), 1),
        (factory.documentSet(id=2, title="title2", query=Some("query2")), 1)
      )
      $("#error-list-modal").length must beEqualTo(1)
    }
  }
}
