package controllers.forms

import play.api.data.Form
import play.api.data.Forms

import org.overviewproject.tree.orm.DocumentSet

object DocumentSetForm {
  case class Credentials(val username: Option[String], val password: Option[String])

  type FormType = Form[(DocumentSet,Credentials, Boolean)]

  def apply() : FormType = {
    Form(
      Forms.mapping(
        "title" -> Forms.nonEmptyText,
        "query" -> Forms.nonEmptyText,
        "documentcloud_username" -> Forms.optional(Forms.text),
        "documentcloud_password" -> Forms.optional(Forms.text),
        "split_documents" -> Forms.boolean
      )
      ((title, query, username, password, splitDocuments) => (DocumentSet(title=title, query=Some(query)), Credentials(username, password), splitDocuments))
      ((tuple) => Some((tuple._1.title, tuple._1.query.getOrElse(""), tuple._2.username, tuple._2.password, tuple._3)))
    )
  }
}
