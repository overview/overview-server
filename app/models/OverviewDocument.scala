package models

import org.overviewproject.tree.orm.{ Document, DocumentSet }
import models.orm.Schema

/** A document in the database */
sealed trait OverviewDocument {
  /** database ID */
  val id: Long

  /** Description of the document. (Empty string is allowed.) */
  val description: String

  /** Optional title of the document */
  val title: Option[String]

  /** Optional text of the document. (We show it if we have it.) */
  val text: Option[String]

  /** User-provided ID of the document.
    *
    * This is nothing but metadata. We do not enforce anything on it.
    */
  val suppliedId: Option[String]

  /** Optional URL of the document */
  val url: Option[String]
  
  /** URL to view the document.
    *
    * @param pattern A pattern for Overview's fallback endpoint, like "http://localhost/documents/{0}"
    */
  def urlWithFallbackPattern(pattern: String) : String = {
    url.getOrElse(pattern.replace("{0}", "" + id))
  }
}

object OverviewDocument {
  val DocumentCloudUrlPrefix = "https://www.documentcloud.org/documents/"

  case class OverviewDocumentImpl(val ormDocument: Document) extends OverviewDocument {
    override val id = ormDocument.id
    override val description = ormDocument.description
    override val title = ormDocument.title
    override val suppliedId = ormDocument.suppliedId.orElse(ormDocument.documentcloudId)
    override val text = ormDocument.text
    override val url : Option[String] = ormDocument.url match {
      case Some(url) => Some(url)
      case None => ormDocument.documentcloudId match {
        case Some(id) => Some(DocumentCloudUrlPrefix + id)
        case None => None
      }
    }
  }

  /** Factory method */
  def apply(ormDocument: Document) : OverviewDocument = OverviewDocumentImpl(ormDocument)
}
