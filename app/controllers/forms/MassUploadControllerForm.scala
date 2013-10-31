package controllers.forms

import play.api.data.Form
import play.api.data.Forms._


object MassUploadControllerForm {
  def apply() = Form(
    tuple(
      "name" -> text.verifying(validation.notWhitespaceOnly),
      "lang" -> text.verifying(validation.supportedLang),
      "supplied_stop_words" -> optional(text)))
}
