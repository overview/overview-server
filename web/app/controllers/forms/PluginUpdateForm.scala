package controllers.forms

import play.api.data.{Form,Forms}

import com.overviewdocs.models.Plugin

object PluginUpdateForm {
  def apply(): Form[Plugin.UpdateAttributes] = {
    Form(
      Forms.mapping(
        "name" -> Forms.nonEmptyText,
        "description" -> Forms.nonEmptyText,
        "url" -> Forms.nonEmptyText,
        "serverUrlFromPlugin" -> Forms.optional(Forms.nonEmptyText),
        "autocreate" -> Forms.default(Forms.boolean, false),
        "autocreateOrder" -> Forms.default(Forms.number, 0)
      )(Plugin.UpdateAttributes.apply)(Plugin.UpdateAttributes.unapply)
    )
  }
}
