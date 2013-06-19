package views.json.Tag

import org.specs2.mutable.Specification

import models.orm.Tag

class updateSpec extends Specification {
  "Json for update tag result" should {
    "contain tag attributed" in {
      val id = 34l
      val name = "tag"
      val color = "aabbcc"
      val colorForJs = '#' + color

      val tag = Tag(id=id, documentSetId=1L, name=name, color=color)

      val tagJson = views.json.Tag.update(tag).toString

      tagJson must /("id" -> id)
      tagJson must /("name" -> name)
      tagJson must /("color" -> colorForJs)
    }
  }
}

