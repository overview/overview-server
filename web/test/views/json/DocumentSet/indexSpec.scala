package views.html.DocumentSet

import com.overviewdocs.models.{DocumentSet,ImportJob}
import models.pagination.Page

class indexSpec extends views.ViewSpecification {
  trait BaseScope extends HtmlViewSpecificationScope {
    def documentSets: Vector[(DocumentSet,Set[ImportJob],Int)] = Vector()
    def documentSetsPage = Page(documentSets)

    val assets = mock[controllers.AssetsFinder]
    assets.path(any) returns "/asset-path"
    val view = new index(assets, new MockMainWithSidebar)

    def result = view(fakeUser, documentSetsPage)
  }

  "DocumentSet.index" should {
    "not show DocumentSets if there are none" in new BaseScope {
      $(".document-sets").length must beEqualTo(0)
    }

    "show DocumentSets if there are some" in new BaseScope {
      override def documentSets = Vector((factory.documentSet(), Set(), 1))
      $(".document-sets").length must beEqualTo(1)
    }

    "render DocumentSets if there are some" in new BaseScope {
      override def documentSets = Vector(
        (factory.documentSet(id=1, title="title1", query=Some("query1")), Set(), 1),
        (factory.documentSet(id=2, title="title2", query=Some("query2")), Set(), 1)
      )
      $(".document-sets").length must equalTo(1)
      $(".document-sets li[data-document-set-id='1']").length must beEqualTo(1)
      $(".document-sets li[data-document-set-id='2']").length must beEqualTo(1)
    }
    
    "define error-list popup if there are DocumentSets" in new BaseScope {
      override def documentSets = Vector(
        (factory.documentSet(id=1, title="title1", query=Some("query1")), Set(), 1),
        (factory.documentSet(id=2, title="title2", query=Some("query2")), Set(), 1)
      )
      $("#error-list-modal").length must beEqualTo(1)
    }
  }
}
