package controllers.forms

import play.api.data.{Form,Forms}

import org.overviewproject.models.Tree

object TreeUpdateAttributesForm {
  def apply() = Form(
    Forms.mapping("title" -> Forms.nonEmptyText)
    ((title) => Tree.UpdateAttributes(title=title))
    ((attributes) => Some((attributes.title)))
  )
}
