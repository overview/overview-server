package org.overviewproject.upgrade.reindex_documents

import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.{GetResult,StaticQuery => Q}

class DocumentsImpl(tag: Tag) extends Table[Document](tag, "document") {
  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def text = column[Option[String]]("text")
  def title = column[Option[String]]("title")
  def suppliedId = column[Option[String]]("supplied_id")

  // Our DB has NULLs it shouldn't have, so we have syntax we shouldn't.
  def * = (id, documentSetId, text, title, suppliedId)
    .<>[Document,Tuple5[Long,Long,Option[String],Option[String],Option[String]]](
      (t: Tuple5[Long,Long,Option[String],Option[String],Option[String]]) => Document(
        t._1,
        t._2,
        t._3.getOrElse(""),
        t._4.getOrElse(""),
        t._5.getOrElse("")
      ), (d: Document) => Some(d.id, d.documentSetId, Some(d.text), Some(d.title), Some(d.suppliedId)))
}
object Documents extends TableQuery(new DocumentsImpl(_))

class Database(url: PostgresUrl) {
  lazy val db = Database.forURL(url.toJdbcUrl)
  lazy val session = db.createSession

  lazy val documentsByDocumentSetId = Compiled { (documentSetId: Column[Long]) =>
    Documents.filter(_.documentSetId === documentSetId)
  }

  def getDocumentSetIds: Seq[Long] = {
    Q.queryNA[Long]("SELECT id FROM document_set ORDER BY id").list(session)
  }

  def forEachBatchOfDocumentsInSet(documentSetId: Long, nPerBatch: Int)(f: Seq[Document] => Unit): Unit = {
    val query = documentsByDocumentSetId(documentSetId)
    SlickQueryBatcher.batch(query, nPerBatch)
      .iterator(session)
      .grouped(nPerBatch)
      .foreach(f)
  }
}
