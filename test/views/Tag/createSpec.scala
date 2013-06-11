package views.json.Tag

import org.specs2.mutable.Specification

import models.orm.Tag

class createSpec extends Specification {
  "Json for create tag result" should {
    "contain tag id and name" in {
      val tagId = 43l
      val tagName = "tagname"

      val tag = Tag(id=tagId, documentSetId=1L, name=tagName, color=Some("abcdef"))
      
      val tagJson = views.json.Tag.create(tag).toString

      tagJson must /("id" -> tagId)
      tagJson must /("name" -> tagName)
    }
  }
}
