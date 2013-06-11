package views.json.helper

import play.api.libs.json.Json.toJson
import org.overviewproject.tree.orm.DocumentProcessingError
import org.specs2.specification.Scope

import org.overviewproject.test.DbSpecification
import models.core._

class ModelJsonConvertersSpec extends DbSpecification {

  "JsonDocumentIdList" should {
    import views.json.helper.ModelJsonConverters.JsonDocumentIdList

    "write documentIdList attributes" in {
      val ids = List(10l, 20l, 34l)
      val count = 45l
      val documentIdList = DocumentIdList(ids, count)

      val documentIdListJson = toJson(documentIdList).toString

      documentIdListJson must contain("\"docids\":" + ids.mkString("[", ",", "]"))
      documentIdListJson must /("n" -> count)
    }
  }
}
