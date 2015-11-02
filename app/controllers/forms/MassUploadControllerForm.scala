package controllers.forms

import play.api.data.Form
import play.api.data.Forms._

object MassUploadControllerForm {
  def new_ = Form(tuple(
    "name" -> text.verifying(validation.notWhitespaceOnly),
    "lang" -> text.verifying(validation.supportedLang),
    "split_documents" -> default(boolean, false)
  ))

  def edit = Form(tuple(
    "lang" -> text.verifying(validation.supportedLang),
    "split_documents" -> default(boolean, false)
  ))
}
