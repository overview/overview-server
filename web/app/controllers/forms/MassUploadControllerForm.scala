package controllers.forms

import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject

object MassUploadControllerForm {
  def new_ = Form(tuple(
    "name" -> text.verifying(validation.notWhitespaceOnly),
    "lang" -> text.verifying(validation.supportedLang),
    "split_documents" -> default(boolean, false),
    "ocr" -> default(boolean, true),
    "metadata_json" -> default(of(OverviewFormats.metadataJson), JsObject(Seq()))
  ))

  def edit = Form(tuple(
    "lang" -> text.verifying(validation.supportedLang),
    "split_documents" -> default(boolean, false),
    "ocr" -> default(boolean, true),
    "metadata_json" -> default(of(OverviewFormats.metadataJson), JsObject(Seq()))
  ))
}
