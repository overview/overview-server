package models

import org.overviewproject.tree.orm.Document
import models.orm.{ DocumentSet, Schema }

/** A document in the database */
sealed trait OverviewDocument {
  /** database ID */
  val id: Long

  /** DocumentSet (models.orm.DocumentSet.
    * FIXME: should be models.OverviewDocumentSet, but we don't have one
    */
  def documentSet: models.orm.DocumentSet

  /** Title of the document. (Empty string is allowed.) */
  val title: String

  /** URL to view the document.
    *
    * @param pattern A pattern for Overview's fallback endpoint, like "http://localhost/documents/{0}"
    */
  def url(pattern: String) : String
}

object OverviewDocument {
  trait OverviewDocumentImpl extends OverviewDocument {
    import models.orm.Schema.documentSetDocuments
    
    protected val ormDocument: Document

    override val id = ormDocument.id
    override lazy val documentSet = documentSetDocuments.right(ormDocument).single
    override val title = ormDocument.title
    override def url(pattern: String) : String = {
      ormDocument.url.getOrElse(pattern.replace("{0}", "" + id))
    }
  }

  case class CsvImportDocument(protected val ormDocument: Document) extends OverviewDocumentImpl {
    private def throwOnNull = throw new Exception("CsvImportDocument has NULL values it should not have")

    /** Full-text contents of the document */
    lazy val text: String = ormDocument.text.getOrElse(throwOnNull)

    /** User-provided ID of the document.
      *
      * This is nothing but metadata. We do not enforce anything on it.
      */
    lazy val suppliedId: Option[String] = ormDocument.suppliedId

    /** User-provided URL for displaying the document.
      *
      * This is metadata. We can use it to render the document differently.
      */
    lazy val suppliedUrl: Option[String] = ormDocument.url

    /** User-provided URL, if it begins with https:// */
    lazy val secureSuppliedUrl : Option[String] = ormDocument.url.filter(_.startsWith("https://"))
  }

  case class DocumentCloudDocument(protected val ormDocument: Document) extends OverviewDocumentImpl {
    private def throwOnNull = throw new Exception("DocumentCloudDocument has NULL values it should not have")

    /** DocumentCloud document ID */
    lazy val documentcloudId: String = ormDocument.documentcloudId.getOrElse(throwOnNull)

    /** URL to view the document on DocumentCloud */
    override def url(pattern: String) : String = {
      "https://www.documentcloud.org/documents/" + documentcloudId
    }
  }

  /** Factory method */
  def apply(ormDocument: Document) : OverviewDocument = {
    ormDocument.documentType.value match {
      case "CsvImportDocument" => CsvImportDocument(ormDocument)
      case "DocumentCloudDocument" => DocumentCloudDocument(ormDocument)
      case _ => throw new Exception("Impossible document type " + ormDocument.documentType.value)
    }
  }

  /** Lookup */
  def findById(id: Long) : Option[OverviewDocument] = {
    import org.squeryl.PrimitiveTypeMode._
    Schema.documents.lookup(id).map(OverviewDocument.apply)
  }
}
