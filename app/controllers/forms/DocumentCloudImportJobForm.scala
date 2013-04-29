package controllers.forms

import play.api.data.{ Form, Forms }

import models.{ DocumentCloudCredentials, DocumentCloudImportJob }

object DocumentCloudImportJobForm {
  private def buildJob(ownerEmail: String)(
    title: String,
    projectId: Long,
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
        "project_id" -> Forms.longNumber,
        "documentcloud_username" -> Forms.optional(Forms.text),
        "documentcloud_password" -> Forms.optional(Forms.text),
        "split_documents" -> Forms.optional(Forms.boolean)
      )
      (buildJob(ownerEmail))
      ((job) => Some(job.title, job.projectId, job.credentials.map(_.username), job.credentials.map(_.password), Some(job.splitDocuments)))
    )
  }
}
