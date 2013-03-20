package views.json.Document

import play.api.libs.json.JsValue
import play.api.libs.json.Json

import models.{ OverviewDocument, TwitterTweet }

object show {
  private def baseProperties(d: OverviewDocument) = {
    val title = d.title.getOrElse("")
    val heading = if (title.length > 0) title else d.description

    Json.obj(
      "id" -> d.id,
      "heading" -> heading
    )
  }

  private def documentCloudProperties(d: OverviewDocument.DocumentCloudDocument) = {
    Json.obj(
      "documentCloudUrl" -> d.url
    )
  }

  private def csvImportProperties(d: OverviewDocument.CsvImportDocument) = {
    d.twitterTweet match {
      case Some(t: TwitterTweet) => Json.obj(
        "twitterTweet" -> Json.obj(
          "text" -> t.text,
          "url" -> t.url,
          "username" -> t.username.getOrElse[String]("")
        )
      )
      case _ => Json.obj(
        "suppliedUrl" -> d.suppliedUrl.getOrElse[String](""),
        "secureSuppliedUrl" -> d.secureSuppliedUrl.getOrElse[String](""),
        "text" -> d.text
      )
    }
  }

  def apply(document: OverviewDocument): JsValue = {
    val subProperties = document match {
      case d: OverviewDocument.DocumentCloudDocument => documentCloudProperties(d)
      case d: OverviewDocument.CsvImportDocument => csvImportProperties(d)
    }

    val properties = baseProperties(document) ++ subProperties

    Json.toJson(properties)
  }
}
