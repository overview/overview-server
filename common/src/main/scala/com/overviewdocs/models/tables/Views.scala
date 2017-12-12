package com.overviewdocs.models.tables

import play.api.libs.json.JsObject

import java.sql.Timestamp
import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.{View,ViewDocumentDetailLink,ViewFilter}

class ViewsImpl(tag: Tag) extends Table[View](tag, "view") {
  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def url = column[String]("url") // simpler than java.net.URI
  def serverUrlFromPlugin = column[Option[String]]("server_url_from_plugin")
  def apiToken = column[String]("api_token") // a password, not a foreign key
  def title = column[String]("title")
  def maybeFilterUrl = column[Option[String]]("filter_url")
  def maybeFilterJson = column[Option[JsObject]]("filter_json_text")
  def maybeDocumentDetailLinkUrl = column[Option[String]]("document_detail_link_url")
  def maybeDocumentDetailLinkTitle = column[Option[String]]("document_detail_link_title")
  def maybeDocumentDetailLinkText = column[Option[String]]("document_detail_link_text")
  def maybeDocumentDetailLinkIconClass = column[Option[String]]("document_detail_link_icon_class")
  def createdAt = column[Timestamp]("created_at")

  def * = (
    id,
    documentSetId,
    url,
    serverUrlFromPlugin,
    apiToken,
    title,
    maybeFilterUrl,
    maybeFilterJson,
    maybeDocumentDetailLinkUrl,
    maybeDocumentDetailLinkTitle,
    maybeDocumentDetailLinkText,
    maybeDocumentDetailLinkIconClass,
    createdAt
  ) <> ((ViewsImpl.buildView _).tupled, ViewsImpl.unapplyView)

  def createAttributes = (
    url,
    serverUrlFromPlugin,
    apiToken,
    title,
    createdAt
  ) <> (View.CreateAttributes.tupled, View.CreateAttributes.unapply)
}

object ViewsImpl {
  def buildView(
    id: Long,
    documentSetId: Long,
    url: String,
    serverUrlFromPlugin: Option[String],
    apiToken: String,
    title: String,
    maybeFilterUrl: Option[String],
    maybeFilterJson: Option[JsObject],
    maybeDocumentDetailLinkUrl: Option[String],
    maybeDocumentDetailLinkTitle: Option[String],
    maybeDocumentDetailLinkText: Option[String],
    maybeDocumentDetailLinkIconClass: Option[String],
    createdAt: Timestamp
  ): View = {
    val viewFilter = (maybeFilterUrl, maybeFilterJson) match {
      case (Some(filterUrl), Some(filterJson)) => Some(ViewFilter(filterUrl, filterJson))
      case _ => None
    }

    val documentDetailLink = (maybeDocumentDetailLinkUrl, maybeDocumentDetailLinkTitle, maybeDocumentDetailLinkText, maybeDocumentDetailLinkIconClass) match {
      case (Some(url), Some(title), Some(text), Some(iconClass)) => Some(ViewDocumentDetailLink(url, title, text, iconClass))
      case _ => None
    }

    View(
      id=id,
      documentSetId=documentSetId,
      url=url,
      serverUrlFromPlugin=serverUrlFromPlugin,
      apiToken=apiToken,
      title=title,
      viewFilter=viewFilter,
      documentDetailLink=documentDetailLink,
      createdAt=createdAt
    )
  }

  def unapplyView(view: View) = {
    Some((
      view.id,
      view.documentSetId,
      view.url,
      view.serverUrlFromPlugin,
      view.apiToken,
      view.title,
      view.viewFilter.map(_.url),
      view.viewFilter.map(_.json),
      view.documentDetailLink.map(_.url),
      view.documentDetailLink.map(_.title),
      view.documentDetailLink.map(_.text),
      view.documentDetailLink.map(_.iconClass),
      view.createdAt
    ))
  }
}

object Views extends TableQuery(new ViewsImpl(_))
