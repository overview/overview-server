package views.html.DocumentSet

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.data.{Form,Forms}
import play.api.mvc.Flash

import models.orm.DocumentSet

class indexSpec extends Specification {
  trait ViewContext extends Scope {
    implicit lazy val flash = new Flash()

    var documentSets : Seq[DocumentSet] = Seq()

    implicit lazy val j = jerry(index(documentSets, form).body)
  }

  val form = Form(
    "query" -> Forms.text
  ) 

  def $(selector: java.lang.String)(implicit j: jodd.lagarto.dom.jerry.Jerry) = { j.$(selector) }

  "DocumentSet.index" should {
    "Not show DocumentSets if there are none" in new ViewContext {
      $("ul.document-sets").length must equalTo(0)
    }

    "Show a form for adding a new document set" in new ViewContext {
      $("form").length must equalTo(1)
      $("input[name=query]").length must equalTo(1)
      $("input[type=submit]").length must equalTo(1)
    }

    "Show links to DocumentSets if there are some" in new ViewContext {
      documentSets ++= Seq(
        DocumentSet(1, "query1", Some(10)),
        DocumentSet(2, "query2", Some(15))
      )

      $("ul.document-sets").length must equalTo(1)
      $("ul.document-sets li#document-set-1 a").attr("href") must endWith("/1")
      $("ul.document-sets li#document-set-2").text must contain("query2")
    }
  }
}
