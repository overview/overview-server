package views.json.Tag

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

import com.overviewdocs.models.Tag

class createSpec extends Specification with JsonMatchers {
  "Json for create tag result" should {
    "contain tag id and name" in {
      val tag = Tag(id=43, documentSetId=1L, name="tagname", color="abcdef")
      val tagJson = views.json.Tag.create(tag).toString

      tagJson must /("id" -> 43)
      tagJson must /("name" -> "tagname")
    }
  }
}
