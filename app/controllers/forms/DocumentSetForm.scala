package controllers.forms

import play.api.data.Form
import play.api.data.Forms

import models.orm.DocumentSet

object DocumentSetForm {
  case class Credentials(val username: Option[String], val password: Option[String])

  type FormType = Form[(DocumentSet,Credentials)]

  def apply() : FormType = {
    Form(
      Forms.mapping(
        "title" -> Forms.text,
        "query" -> Forms.text,
        "documentcloud_username" -> Forms.optional(Forms.text),
        "documentcloud_password" -> Forms.optional(Forms.text)
      )
      ((title, query, username, password) => (DocumentSet(0L, title, query), Credentials(username, password)))
      ((tuple) => Some((tuple._1.title, tuple._1.query, tuple._2.username, tuple._2.password)))
    )
  }
}
