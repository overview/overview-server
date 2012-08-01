package views.html.DocumentSet

import play.api.data.{Form,Forms}
import org.specs2.mutable.Specification

import jodd.lagarto.dom.jerry.Jerry.jerry

import models.{DocumentSet,DocumentSetCreationJob}

class indexSpec extends Specification {
  private def documentSet(id: Long, query: String) = {
    val ret = new DocumentSet()
    ret.id = id
    ret.query = query
    ret
  }

  private def documentSetCreationJob(id: Long, query: String, state: DocumentSetCreationJob.JobState) = {
    val ret = new DocumentSetCreationJob()
    ret.id = id
    ret.query = query
    ret.state = state
    ret
  }

  val form = Form(
    Forms.mapping(
      "query" -> Forms.text
    ) ((query) => new DocumentSetCreationJob(query))
      ((job: DocumentSetCreationJob) => Some((job.query)))
  ) 

  def $(selector: java.lang.String)(implicit j: jodd.lagarto.dom.jerry.Jerry) = { j.$(selector) }

  "DocumentSet.index" should {
    "Show DocumentSetCreationJobs if there are some" in {
      val dscj1 = documentSetCreationJob(1, "query1", DocumentSetCreationJob.JobState.Submitted)
      val dscj2 = documentSetCreationJob(2, "query2", DocumentSetCreationJob.JobState.Submitted)

      val html = index(Seq(), Seq(dscj1, dscj2), form).body
      val j = jerry(html)

      j.$("ul.document-set-creation-jobs").text() must contain("query1")
      j.$("ul.document-set-creation-jobs").text() must contain("query2")
    }

    //"Show the DocumentSetCreationJob status" in {
    //  val dscj = documentSetCreationJob(1, "query1", DocumentSetCreationJob.JobState.InProgress)
    //  val html = index(Seq(), Seq(dscj), form).body
    //  html must contain("in progress")
    //}

    "Not show DocumentSetCreationJobs if there aren't any" in {
      implicit val j = jerry(index(Seq(), Seq(), form).body)
      $("ul.document-set-creation-jobs").length must equalTo(0)
    }

    "Show links to DocumentSets if there are some" in {
      val ds1 = documentSet(1, "query1")
      val ds2 = documentSet(2, "query2")

      implicit val j = jerry(index(Seq(ds1, ds2), Seq(), form).body)
      $("ul.document-sets").length must equalTo(1)
      $("ul.document-sets li#document-set-1 a").attr("href") must endWith("/1")
      $("ul.document-sets li#document-set-2").text must contain("query2")
    }

    "Not show links to DocumentSets if there are none" in {
      implicit val j = jerry(index(Seq(), Seq(), form).body)
      $("ul.document-sets").length must equalTo(0)
    }

    "Show a form for adding a new document set" in {
      implicit val j = jerry(index(Seq(), Seq(), form).body)
      $("form").length must equalTo(1)
      $("input[name=query]").length must equalTo(1)
      $("input[type=submit]").length must equalTo(1)
    }
  }
}
