package controllers.forms

import play.api.data.{Form,Forms}

import org.overviewproject.models.Plugin

object PluginCreateForm {
  def apply(): Form[Plugin.CreateAttributes] = {
    Form(
      Forms.mapping(
        "name" -> Forms.nonEmptyText,
        "description" -> Forms.nonEmptyText,
        "url" -> Forms.nonEmptyText
      )(Plugin.CreateAttributes.apply)(Plugin.CreateAttributes.unapply)
    )
  }
}
