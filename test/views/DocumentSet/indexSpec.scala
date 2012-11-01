package views.html.DocumentSet

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.mvc.Flash
import play.api.Play.{start, stop}
import play.api.test.FakeApplication

import models.orm.DocumentSet

class indexSpec extends Specification {
  trait ViewContext extends Scope {
    implicit lazy val flash = new Flash()
    lazy val user = new models.orm.User()

    var documentSets : Seq[DocumentSet] = Seq()

    implicit lazy val j = jerry(index(user, documentSets, form).body)
  }

  val form = controllers.forms.DocumentSetForm()

  def $(selector: java.lang.String)(implicit j: jodd.lagarto.dom.jerry.Jerry) = { j.$(selector) }

  step(start(FakeApplication()))
    
  "DocumentSet.index" should {
    "Not show DocumentSets if there are none" in new ViewContext {
      $("ul.document-sets").length must equalTo(0)
    }

    "Show a form for adding a new document set" in new ViewContext {
      $("form").length must equalTo(5)
      $("input[name=query]").length must equalTo(4)
      $("input[type=submit]").length must equalTo(1)
    }

    "Show links to DocumentSets if there are some" in new ViewContext {
      documentSets ++= Seq(
        DocumentSet(1, "title1", "query1", providedDocumentCount=Some(10)),
        DocumentSet(2, "title2", "query2", providedDocumentCount=Some(15))
      )

      $("ul.document-sets").length must equalTo(1)
      $("ul.document-sets li#document-set-1 a").attr("href") must endWith("/1")
      $("ul.document-sets li#document-set-2").text must contain("title2")
    }
  }

  step(stop)
}
