package controllers.forms

import play.api.data.Form
import play.api.data.Forms._

object UploadControllerForm {
  def apply(): Form[(String, Option[String], Option[String])] = {
    Form(
      tuple(
        "lang" -> text.verifying(validation.supportedLang),
        "supplied_stop_words" -> optional(text),
    	"important_words" -> optional(text)))
  }
}