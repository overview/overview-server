package views.json.Tag

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

import com.overviewdocs.models.Tag

class updateSpec extends Specification with JsonMatchers {
  "Json for update tag result" should {
    "contain tag attributed" in {
      val tag = Tag(id=34L, documentSetId=1L, name="tagname", color="abcdef")
      val tagJson = views.json.Tag.update(tag).toString

      tagJson must /("id" -> 34)
      tagJson must /("name" -> "tagname")
      tagJson must /("color" -> "#abcdef")
    }
  }
}

