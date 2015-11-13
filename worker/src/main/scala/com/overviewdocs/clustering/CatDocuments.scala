package com.overviewdocs.clustering

import java.io.{BufferedWriter,OutputStream,OutputStreamWriter}
import java.nio.charset.StandardCharsets

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.Document
import com.overviewdocs.models.tables.{DocumentTags,Documents}

/** Iterates over Documents from the database.
  *
  * The constructor and foreach methods are slow, and they block. Beware.
  *
  * @param documentSetId Filter by document set ID.
  * @param maybeTagId If set, filter by the given tag ID.
  */
class CatDocuments(
  documentSetId: Long,
  maybeTagId: Option[Long],
  pageSize: Int = 50 // max ~1MB/doc
) extends HasBlockingDatabase {
  private val allIds: Seq[Long] = {
    import database.api._
    blockingDatabase.option(
      sql"""SELECT sorted_document_ids FROM document_set WHERE id = $documentSetId""".as[Seq[Long]]
    ).getOrElse(Seq())
  }

  private val taggedIds = {
    import database.api._

    maybeTagId match {
      case None => allIds
      case Some(tagId) => {
        val usefulIds: Set[Long] = blockingDatabase.seq(
          DocumentTags
            .filter(_.tagId === tagId)
            .map(_.documentId)
        ).toSet
        allIds.filter(usefulIds.contains _)
      }
    }
  }

  def length: Int = taggedIds.length

  def foreach(f: Document => Unit): Unit = {
    import database.api._

    taggedIds.grouped(pageSize).foreach { someIds =>
      val documents: Map[Long,Document] = {
        blockingDatabase.seq(Documents.filter(_.id inSet someIds)).map(d => (d.id -> d)).toMap
      }

      someIds.foreach { id =>
        val document: Document = documents(id) // Missing? Crash.
        f(document)
      }
    }
  }
}
