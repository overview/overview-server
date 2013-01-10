package views.json.DocumentList

import models.core.Document
import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson

class DocumentListSpec extends Specification {

  "DocumentList view generated Json" should {

    "contain documents and total_items" in {
      val documents = List(
        Document(10, "description1", Some("title1"), Some("documentCloudId"), Seq(), Seq(22l)),
        Document(20, "description2", Some("title2"), Some("documentCloudId"), Seq(), Seq(22l)),
        Document(30, "description3", Some("title3"), Some("documentCloudId"), Seq(), Seq(22l)))
      val totalCount = 13l

      val documentListJson = show(documents, totalCount).toString

      documentListJson must beMatching(".*\"documents\":\\[(.*description.*,?){3}\\].*".r)
      documentListJson must /("total_items" -> totalCount)
    }
  }
}
