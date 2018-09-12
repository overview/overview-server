package com.overviewdocs.csv

import scala.collection.mutable
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.Tag
import com.overviewdocs.models.tables.{DocumentTags,Tags}
import com.overviewdocs.util.{BulkDocumentWriter,TagColorList}

/** Batches documents and writes them all at once.
  *
  * Usage:
  *
  *     writer = new CsvDocumentWriter(123L)
  *     writer.add(doc1)
  *     writer.add(doc2)
  *     writer.flush
  *
  * Instances are not thread-safe; use one thread at a time.
  */
class CsvDocumentWriter(
  val documentSetId: Long,
  val maxExistingDocumentId: Option[Long] = None,
  val existingTags: Seq[Tag] = Seq(),
  private val bulkDocumentWriter: BulkDocumentWriter = BulkDocumentWriter.forDatabaseAndSearchIndex
) extends HasDatabase {
  import database.api._
  import database.executionContext

  private val csvDocuments = mutable.Buffer[CsvDocument]()
  private val tagNameToId = mutable.Map[String,Long]() ++ existingTags.map(t => t.name -> t.id)
  private var nextDocumentId: Long = maxExistingDocumentId.map(_ + 1).getOrElse(documentSetId << 32L)

  /** Adds a document to the batch. */
  def add(csvDocument: CsvDocument): Unit = {
    csvDocuments.+=(csvDocument)
  }

  /** Truncate tag names so they fit in the database (and in common sense).
    */
  private def truncate(tagName: String): String = {
    tagName.take(100)
  }

  /** Writes all documents and new tags to the database. */
  def flush: Future[Unit] = {
    val documentTags = mutable.Buffer[(Long,Long)]()  // document ID -> tag ID
    val newTags = mutable.Map[String,mutable.Buffer[Long]]() // tag name -> document IDs

    csvDocuments.foreach { csvDocument =>
      val document = csvDocument.toDocument(nextDocumentId, documentSetId)
      nextDocumentId += 1

      bulkDocumentWriter.add(document)

      csvDocument.tags.map(truncate _).foreach { tagName =>
        tagNameToId.get(tagName) match {
          case None => newTags.getOrElseUpdate(tagName, mutable.Buffer[Long]()).+=(document.id)
          case Some(tagId) => documentTags.+=(document.id -> tagId)
        }
      }
    }

    for {
      newTagNameToId <- flushTags(newTags.keys).map(_.toMap)
      _ <- bulkDocumentWriter.flush
      _ <- flushDocumentTags(documentTags ++ newTags.toSeq.flatMap(t => t._2.map(documentId => documentId -> newTagNameToId(t._1))))
    } yield {
      tagNameToId ++= newTagNameToId
      csvDocuments.clear
    }
  }

  private lazy val tagInserter = {
    Tags
      .map(t => (t.documentSetId, t.name, t.color))
      .returning(Tags.map(t => (t.name, t.id)))
  }

  private lazy val documentTagInserter = {
    DocumentTags
      .map(dt => (dt.documentId, dt.tagId))
  }

  /** Flushes new Tags to the database and returns a mapping from name to ID. */
  private def flushTags(tagNames: Iterable[String]): Future[Seq[(String,Long)]] = {
    database.run(tagInserter.++=(tagNames.map(name => (documentSetId, name, TagColorList.forString(name)))))
  }

  /** Writes to DocumentTags. */
  private def flushDocumentTags(documentTags: Seq[(Long,Long)]): Future[Unit] = {
    database.runUnit(documentTagInserter.++=(documentTags))
  }
}
