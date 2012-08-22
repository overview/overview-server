package views.html.DocumentSet

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.data.{Form,Forms}
import play.api.mvc.Flash

import models.orm.DocumentSet
import models.orm.DocumentSetCreationJob

class indexSpec extends Specification {
  trait ViewContext extends Scope {
    implicit lazy val flash = new Flash()
  }

  private def documentSet(id: Long, query: String) = {
    new DocumentSet(query)
  }

  private def documentSetCreationJob(id: Long, query: String, state: Int) = {
    new DocumentSetCreationJob(id)
  }

  val form = Form(
      "query" -> Forms.text
  ) 

  def $(selector: java.lang.String)(implicit j: jodd.lagarto.dom.jerry.Jerry) = { j.$(selector) }

  "DocumentSet.index" should {
    "Show DocumentSetCreationJobs if there are some" in new ViewContext {
      val dscj1 = documentSetCreationJob(1, "query1", 0)
      val dscj2 = documentSetCreationJob(2, "query2", 0)

      val html = index(Seq(), form).body
      val j = jerry(html)

      j.$("ul.document-set-creation-jobs").text() must contain("query1")
      j.$("ul.document-set-creation-jobs").text() must contain("query2")
    }.pendingUntilFixed

    //"Show the DocumentSetCreationJob status" in new ViewContext {
    //  val dscj = documentSetCreationJob(1, "query1", DocumentSetCreationJob.JobState.InProgress)
    //  val html = index(Seq(), Seq(dscj), form).body
    //  html must contain("in progress")
    //}

    "Not show DocumentSetCreationJobs if there aren't any" in new ViewContext {
      implicit val j = jerry(index(Seq(), form).body)
      $("ul.document-set-creation-jobs").length must equalTo(0)
    }
// FIXME This doesn't compile
//    "Show links to DocumentSets if there are some" in new ViewContext {
//      val ds1 = documentSet(1, "query1")
//      val ds2 = documentSet(2, "query2")
//
//      implicit val j = jerry(index(Seq(ds1, ds2), Seq(), form).body)
//      $("ul.document-sets").length must equalTo(1)
//      $("ul.document-sets li#document-set-1 a").attr("href") must endWith("/1")
//      $("ul.document-sets li#document-set-2").text must contain("query2")
//    }

    "Not show links to DocumentSets if there are none" in new ViewContext {
      implicit val j = jerry(index(Seq(), form).body)
      $("ul.document-sets").length must equalTo(0)
    }

    "Show a form for adding a new document set" in new ViewContext {
      implicit val j = jerry(index(Seq(), form).body)
      $("form").length must equalTo(1)
      $("input[name=query]").length must equalTo(1)
      $("input[type=submit]").length must equalTo(1)
    }
  }
}
