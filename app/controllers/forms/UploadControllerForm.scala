package controllers.forms

import play.api.data.Form
import play.api.data.Forms._

object UploadControllerForm {
  def apply(): Form[String] = {
    Form("lang" -> text.verifying(validation.supportedLang))
  }
}