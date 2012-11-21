package views.json.Tag

import helpers.TestTag
import models.PersistentTagInfo
import models.core.{Document, DocumentIdList}
import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson

class addSpec extends Specification {
  
  "Json for tag add result" should {

    "contain tagid, added count, and total count" in {
      val tagId = 44l
      val tag: PersistentTagInfo = TestTag(tagId, "name", None, DocumentIdList(Nil, 0) )
      val addedCount = 20l
      val documentId = 1l
      val documents = Seq(Document(documentId, "title", Some("documentCloudId"), Seq(5l, 15l), Seq(22l)))
      
      val resultJson = toJson(views.json.Tag.add(tag, addedCount, documents)).toString 
      
      resultJson must /("num_added" -> addedCount)
      resultJson must /("tag") */("id" ->  tagId)
      resultJson must /("documents") */("id" -> documentId)
    }
  }

}
