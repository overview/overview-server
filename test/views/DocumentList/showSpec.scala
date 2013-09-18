package views.json.DocumentList

import org.specs2.mutable.Specification

import org.overviewproject.tree.orm.Document
import models.{ResultPage,ResultPageDetails}

class showSpec extends Specification {

  "DocumentList view generated Json" should {

    "contain documents and total_items" in {
      val documents = Seq(
        (Document(
          documentSetId=1L,
          id=5L,
          description="description",
          title=Option("title"),
          suppliedId=Some("suppliedId"),
          text=Some("text"),
          url=Some("http://url.example.org"),
          documentcloudId=Some("documentcloudId")
        ), Seq(1L), Seq(2L))
      )

      val resultPage = ResultPage[(Document,Seq[Long],Seq[Long])](
        items=documents,
        pageDetails=ResultPageDetails(pageSize=1,pageNum=3,totalLength=10)
      )

      val documentListJson = show(resultPage).toString

      documentListJson must /("total_items" -> 10)
      documentListJson must /("documents") */("id" -> 5)
    }
  }
}
