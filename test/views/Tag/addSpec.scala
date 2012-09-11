package views.json.Tag

import models.core.{Document, DocumentIdList, Tag}
import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson

class addSpec extends Specification {
  
  "Json for tag add result" should {
    
    "contain tagid, added count, and total count" in {
      val tagId = 44l
      val tag = Tag(tagId, "name", DocumentIdList(Nil, 0) )
      val addedCount = 20l
      val documentId = 1l
      val documents = Seq(Document(documentId, "title", "documentCloudId", Seq(5l, 15l)))
      
      val resultJson = toJson(views.json.Tag.add(tag, addedCount, documents)).toString 
      
      resultJson must /("num_added" -> addedCount)
      resultJson must /("tag") */("id" ->  tagId)
      resultJson must /("documents") */("id" -> documentId)
    }
  }

}
