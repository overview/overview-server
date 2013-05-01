package controllers.forms

import play.api.data.{ Form, Forms }
import play.api.data.validation.Constraints

import models.{ DocumentCloudCredentials, DocumentCloudImportJob }

object DocumentCloudImportJobForm {
  private def buildJob(ownerEmail: String)(
    title: String,
    projectId: String,
    username: Option[String],
    password: Option[String],
    splitDocuments: Option[Boolean]
  ) = {
    val credentials = for {
      definedUsername <- username;
      definedPassword <- password
    } yield DocumentCloudCredentials(definedUsername, definedPassword)
    DocumentCloudImportJob(
      ownerEmail=ownerEmail,
      title=title,
      projectId=projectId,
      credentials=credentials,
      splitDocuments=splitDocuments.getOrElse(false)
    )
  }

  def apply(ownerEmail: String) : Form[DocumentCloudImportJob] = {
    Form(
      Forms.mapping(
        "title" -> Forms.nonEmptyText,
        "project_id" -> Forms.nonEmptyText.verifying(Constraints.pattern("""^[-a-z0-9]+$""".r)),
        "documentcloud_username" -> Forms.optional(Forms.text),
        "documentcloud_password" -> Forms.optional(Forms.text),
        "split_documents" -> Forms.optional(Forms.boolean)
      )
      (buildJob(ownerEmail))
      ((job) => Some(job.title, job.projectId, job.credentials.map(_.username), job.credentials.map(_.password), Some(job.splitDocuments)))
    )
  }
}
