package views.json.Tag

import helpers.TestTag
import models.PersistentTagInfo
import models.core.{Document, DocumentIdList}
import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson


class removeSpec extends Specification {
 
  "Json for tag remove result" should {

    "contain tagid, removed count, and total count" in {
      val tagId = 44l
      val tag = TestTag(tagId, "name", None, DocumentIdList(Nil, 0) )
      val removedCount = 20l
      val documentId = 1l
      val documents = Seq(Document(documentId, "title", "documentCloudId", Seq(), Seq(22l)))
      
      val resultJson = toJson(views.json.Tag.remove(tag, removedCount, documents)).toString 
      
      resultJson must /("num_removed" -> removedCount)
      resultJson must /("tag") */("id" ->  tagId)
      resultJson must /("documents") */("id" -> documentId)
    }
  }

}
