package controllers.forms

import org.overviewproject.test.Specification
import org.specs2.specification.Scope
import play.api.data.Form

import models.{ DocumentCloudCredentials, DocumentCloudImportJob }

class DocumentCloudImportJobFormSpec extends Specification {
  "DocumentCloudImportJobForm" should {
    trait FormScope extends Scope {
      def ownerEmail: String = "user@example.org"
      def data: Map[String, String] = Map()
      def form: Form[DocumentCloudImportJob] = DocumentCloudImportJobForm(ownerEmail)
      def baseJob = DocumentCloudImportJob(
        ownerEmail = ownerEmail,
        title = "",
        query = "projectid:1-project",
        lang = "en",
        credentials = None,
        splitDocuments = false,
        suppliedStopWords = "",
        importantWords = "")
    }

    trait BasicFormScope extends FormScope {
      override def data = Map("title" -> "title", "query" -> "projectid:1-project", "lang" -> "en")
      override def baseJob = super.baseJob.copy(title = "title", query = "projectid:1-project")
    }

    "accept a title, query, and lang" in new FormScope {
      override def data = Map(
        "title" -> "title",
        "query" -> "1-query",
        "lang" -> "en")
      form.bind(data).value must beEqualTo(Some(baseJob.copy(
        title = "title",
        query = "1-query")))
    }

    "fail if there is no title" in new FormScope {
      override def data = Map("query" -> "projectid:1-project")
      form.bind(data).error("title") must beSome
    }

    "fail if there is no query" in new FormScope {
      override def data = Map("title" -> "title")
      form.bind(data).error("query") must beSome
    }

    "fail if query is only whitespace" in new FormScope {
      override def data = Map("title" -> "title", "query" -> "   ")
      form.bind(data).error("query") must (beSome)
    }

    "fail if there is no lang" in new FormScope {
      override def data = Map(
        "title" -> "title",
        "query" -> "1-query")
        
      form.bind(data).error("lang") must beSome
    }

    "fail if lang is not supported" in new BasicFormScope {
      override def data = super.data ++ Map("lang" -> "not a valid language")

      form.bind(data).error("lang") must beSome
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

    "set lang" in new BasicFormScope {
      val lang = "sv"
      override def data = super.data ++ Map("lang" -> lang)

      form.bind(data).value.map(_.lang) must beSome(lang)
    }

    "add credentials if given" in new BasicFormScope {
      val credentials = DocumentCloudCredentials(
        username = "user@documentcloud.org",
        password = "documentcloud-password")
      override def data = super.data ++ Map(
        "documentcloud_username" -> credentials.username,
        "documentcloud_password" -> credentials.password)
      form.bind(data).value must beEqualTo(Some(baseJob.copy(credentials = Some(credentials))))
    }

    "add no credentials if the given credentials are incomplete" in new BasicFormScope {
      override def data = super.data ++ Map("documentcloud_username" -> "user@documentcloud.org")
      form.bind(data).value must beSome(baseJob)
    }

    "add supplied stopwords if given" in new BasicFormScope {
      val suppliedStopWords = "ignore these words"
      override def data = super.data ++ Map("supplied_stop_words" -> suppliedStopWords)

      form.bind(data).value.map(_.suppliedStopWords) must beSome(suppliedStopWords)
    }

    "add important words if given" in new BasicFormScope {
      val importantWords = "these words and \\wRegexes[\\d+] REALLY Matter"
      override def data = super.data ++ Map("important_words" -> importantWords)

      form.bind(data).value.map(_.importantWords) must beSome(importantWords)
    }
  }
}
