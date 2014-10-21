package controllers.forms

import play.api.data.{Form,Forms}

import org.overviewproject.models.Plugin

object PluginUpdateForm {
  def apply(): Form[Plugin.UpdateAttributes] = {
    Form(
      Forms.mapping(
        "name" -> Forms.nonEmptyText,
        "description" -> Forms.nonEmptyText,
        "url" -> Forms.nonEmptyText
      )(Plugin.UpdateAttributes.apply)(Plugin.UpdateAttributes.unapply)
    )
  }
}
