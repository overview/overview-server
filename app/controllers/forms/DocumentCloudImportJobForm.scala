package controllers.forms

import play.api.data.{ Form, Forms }
import play.api.data.validation.Constraints

import models.{ DocumentCloudCredentials, DocumentCloudImportJob }

object DocumentCloudImportJobForm {
  private def buildJob(ownerEmail: String)(
    title: String,
    query: String,
    lang: String,
    username: Option[String],
    password: Option[String],
    splitDocuments: Option[Boolean],
    suppliedStopWords: Option[String],
    importantWords: Option[String]
  ) = {
    val credentials = for {
      definedUsername <- username;
      definedPassword <- password
    } yield DocumentCloudCredentials(definedUsername, definedPassword)
    DocumentCloudImportJob(
      ownerEmail = ownerEmail,
      title = title,
      query = query, 
      lang = lang,
      credentials = credentials,
      splitDocuments = splitDocuments.getOrElse(false),
      suppliedStopWords = suppliedStopWords.getOrElse(""),
      importantWords = importantWords.getOrElse("")
    )
  }

  def apply(ownerEmail: String) : Form[DocumentCloudImportJob] = {
    Form(
      Forms.mapping(
        "title" -> Forms.nonEmptyText,
        "query" -> Forms.nonEmptyText,
        "lang" -> Forms.text.verifying(validation.supportedLang),
        "documentcloud_username" -> Forms.optional(Forms.text),
        "documentcloud_password" -> Forms.optional(Forms.text),
        "split_documents" -> Forms.optional(Forms.boolean),
        "supplied_stop_words" -> Forms.optional(Forms.text),
        "important_words" -> Forms.optional(Forms.text)
      )
      (buildJob(ownerEmail))
      ((job) => Some(job.title, job.query, job.lang, job.credentials.map(_.username), job.credentials.map(_.password), Some(job.splitDocuments), Some(job.suppliedStopWords), Some(job.importantWords)))
    )
  }
}
