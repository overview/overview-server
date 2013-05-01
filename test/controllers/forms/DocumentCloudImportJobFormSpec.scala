package controllers.forms

import org.overviewproject.test.Specification
import org.specs2.specification.Scope
import play.api.data.Form

import models.{ DocumentCloudCredentials, DocumentCloudImportJob }

class DocumentCloudImportJobFormSpec extends Specification {
  "DocumentCloudImportJobForm" should {
    trait FormScope extends Scope {
      def ownerEmail : String = "user@example.org"
      def data : Map[String,String] = Map()
      def form : Form[DocumentCloudImportJob] = DocumentCloudImportJobForm(ownerEmail)
      def baseJob = DocumentCloudImportJob(
        ownerEmail=ownerEmail,
        title="",
        projectId="1-project",
        credentials=None,
        splitDocuments=false
      )
    }

    trait BasicFormScope extends FormScope {
      override def data = Map("title" -> "title", "project_id" -> "1-project")
      override def baseJob = super.baseJob.copy(title="title", projectId="1-project")
    }

    "accept a title and project_id" in new FormScope {
      override def data = Map(
        "title" -> "title",
        "project_id" -> "1-project"
      )
      form.bind(data).value must beEqualTo(Some(baseJob.copy(
        title="title",
        projectId="1-project"
      )))
    }

    "fail if there is no title" in new FormScope {
      override def data = Map("project_id" -> "1-project")
      form.bind(data).error("title") must beSome
    }

    "fail if there is no project_id" in new FormScope {
      override def data = Map("title" -> "title")
      form.bind(data).error("project_id") must beSome
    }

    "add ownerEmail to the return value" in new BasicFormScope {
      form.bind(data).value.map(_.ownerEmail) must beSome(ownerEmail)
    }

    "default to no splitDocuments" in new BasicFormScope {
      form.bind(data).value.map(_.splitDocuments) must beSome(false)
    }

    "set splitDocuments to true" in new BasicFormScope {
      override def data = super.data ++ Map("split_documents" -> "true")
      form.bind(data).value.map(_.splitDocuments) must beSome(true)
    }

    "set splitDocuments to false" in new BasicFormScope {
      override def data = super.data ++ Map("split_documents" -> "false")
      form.bind(data).value.map(_.splitDocuments) must beSome(false)
    }

    "add credentials if given" in new BasicFormScope {
      val credentials = DocumentCloudCredentials(
        username="user@documentcloud.org",
        password= "documentcloud-password"
      )
      override def data = super.data ++ Map(
        "documentcloud_username" -> credentials.username,
        "documentcloud_password" -> credentials.password
      )
      form.bind(data).value must beEqualTo(Some(baseJob.copy(credentials=Some(credentials))))
    }

    "add no credentials if the given credentials are incomplete" in new BasicFormScope {
      override def data = super.data ++ Map("documentcloud_username" -> "user@documentcloud.org")
      form.bind(data).value must beSome(baseJob)
    }
  }
}
