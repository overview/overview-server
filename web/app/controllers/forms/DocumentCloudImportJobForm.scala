package controllers.forms

import play.api.data.{Form,Forms}

object DocumentCloudImportJobForm {
  case class Record(
    title: String,
    query: String,
    lang: String,
    username: String,
    password: String,
    splitPages: Boolean
  )

  def apply(): Form[Record] = Form(
    Forms.mapping(
      "title" -> Forms.nonEmptyText,
      "query" -> Forms.nonEmptyText,
      "lang" -> Forms.text.verifying(validation.supportedLang),
      "documentcloud_username" -> Forms.default(Forms.text, ""),
      "documentcloud_password" -> Forms.default(Forms.text, ""),
      "split_documents" -> Forms.default(Forms.boolean, false)
    )(Record.apply)(Record.unapply)
  )
}
