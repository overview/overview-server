package controllers.forms

import play.api.data.{Form,Forms}

import com.overviewdocs.models.View

object ViewUpdateAttributesForm {
  def apply() = Form(
    Forms.mapping("title" -> Forms.nonEmptyText)
    ((title) => View.UpdateAttributes(title=title))
    ((attributes) => Some((attributes.title)))
  )
}
