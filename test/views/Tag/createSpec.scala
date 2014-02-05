package views.json.Tag

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

import org.overviewproject.tree.orm.Tag

class createSpec extends Specification with JsonMatchers {
  "Json for create tag result" should {
    "contain tag id and name" in {
      val tagId = 43l
      val tagName = "tagname"

      val tag = Tag(id=tagId, documentSetId=1L, name=tagName, color="abcdef")
      
      val tagJson = views.json.Tag.create(tag).toString

      tagJson must /("id" -> tagId)
      tagJson must /("name" -> tagName)
    }
  }
}
