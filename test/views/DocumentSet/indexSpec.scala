package views.html.DocumentSet

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import play.api.mvc.Flash
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import helpers.FakeOverviewDocumentSet
import models.orm.DocumentSetType._
import models.{ OverviewDocumentSet, OverviewDocumentSetCreationJob, OverviewUser, ResultPage }

class indexSpec extends Specification {

  trait ViewContext extends Scope with Mockito {
    implicit lazy val flash = new Flash()
    lazy val ormUser = new models.orm.User()
    lazy val user = OverviewUser(ormUser)

    val jobsWithDocumentSets : Seq[(OverviewDocumentSetCreationJob, OverviewDocumentSet)] = Seq()
    implicit lazy val jobsPage = ResultPage(jobsWithDocumentSets, 10, 1)

    val documentSets: Seq[OverviewDocumentSet] = Seq()
    implicit lazy val documentSetsPage = ResultPage(documentSets, 10, 1)

    implicit lazy val j = jerry(index(user, documentSetsPage, jobsPage, form).body)
    def $(selector: java.lang.String) = j.$(selector) 
  }

  trait ViewContextWithJob extends ViewContext {
    val job = mock[OverviewDocumentSetCreationJob]
    job.state returns InProgress
    job.stateDescription returns "someKey"
    val documentSet = mock[OverviewDocumentSet.DocumentCloudDocumentSet]
    documentSet.id returns 1L
    documentSet.title returns "Title"
    documentSet.query returns "query"

    override val jobsWithDocumentSets = Seq((job, documentSet))
  }

  val form = controllers.forms.DocumentSetForm()

  step(start(FakeApplication()))

  "DocumentSet.index" should {
    "Not show DocumentSets if there are none" in new ViewContext {
      $(".document-sets").length must beEqualTo(0)
    }

    "Show DocumentSets if there are some" in new ViewContext {
      override val documentSets = Seq(FakeOverviewDocumentSet())
      $(".document-sets").length must beEqualTo(1)
    }

    "Not show Jobs if there are none" in new ViewContext {
      $(".document-set-creation-jobs").length must beEqualTo(0)
    }

    "Show Jobs if there are some" in new ViewContextWithJob {
      $(".document-set-creation-jobs").length must beEqualTo(1)
    }

    "Show DocumentSets UL if there are none, but there are Jobs" in new ViewContextWithJob {
      $(".document-sets ul").length must equalTo(1)
    }

    "Show forms for adding new document sets" in new ViewContext {
      $("form").length must equalTo(2)
      $("input[name=query]").length must equalTo(1)
    }

    "Show links to DocumentSets if there are some" in new ViewContext {
      override val documentSets = Seq(
        FakeOverviewDocumentSet(1, "title1", "query1"),
        FakeOverviewDocumentSet(2, "title2", "query2"))

      $(".document-sets").length must equalTo(1)
      $(".document-sets li#document-set-1 a").attr("href") must endWith("/1")
      $(".document-sets li#document-set-2").text must contain("title2")
    }
    
    "Define error-list popup if there are DocumentSets" in new ViewContext {
      override val documentSets = Seq(
        FakeOverviewDocumentSet(1, "title1", "query1"),
        FakeOverviewDocumentSet(2, "title2", "query2"))

      $("#error-list-modal").length must beEqualTo(1)
    }

    "Define error-list popup if there are jobs" in new ViewContextWithJob {
      $("#error-list-modal").length must beEqualTo(1)
    }
  }
  step(stop)
}
