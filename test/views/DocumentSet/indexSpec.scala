package views.html.DocumentSet

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.mvc.Flash
import play.api.Play.{start, stop}
import play.api.test.FakeApplication

import models.{OverviewDocumentSet,OverviewUser}
import models.orm.DocumentSet
import models.orm.DocumentSetType._

class indexSpec extends Specification {
  trait ViewContext extends Scope {
    implicit lazy val flash = new Flash()
    lazy val ormUser = new models.orm.User()
    lazy val user = OverviewUser(ormUser)

    var documentSets : Seq[OverviewDocumentSet] = Seq()

    implicit lazy val j = jerry(index(user, documentSets, form).body)
  }

  val form = controllers.forms.DocumentSetForm()

  def $(selector: java.lang.String)(implicit j: jodd.lagarto.dom.jerry.Jerry) = { j.$(selector) }

  step(start(FakeApplication()))
    
  "DocumentSet.index" should {
    "Not show DocumentSets if there are none" in new ViewContext {
      $("ul.document-sets").length must equalTo(0)
    }

    "Show forms for adding new document sets" in new ViewContext {
      $("form").length must equalTo(5)
      $("input[name=query]").length must equalTo(4)
    }

    "Show links to DocumentSets if there are some" in new ViewContext {
      documentSets ++= Seq(
        DocumentSet(DocumentCloudDocumentSet, 1, "title1", Some("query1"), providedDocumentCount=Some(10)),
        DocumentSet(DocumentCloudDocumentSet, 2, "title2", Some("query2"), providedDocumentCount=Some(15))
      ).map(OverviewDocumentSet.apply)

      $("ul.document-sets").length must equalTo(1)
      $("ul.document-sets li#document-set-1 a").attr("href") must endWith("/1")
      $("ul.document-sets li#document-set-2").text must contain("title2")
    }
  }

  step(stop)
}
