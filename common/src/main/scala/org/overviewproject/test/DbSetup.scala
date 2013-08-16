package org.overviewproject.test

import anorm._
import java.sql.Connection
import org.overviewproject.test.IdGenerator._
import org.overviewproject.util.DocumentSetVersion

object DbSetup {

  private def failInsert = { throw new Exception("failed insert") }

  def clearEntireDatabaseYesReally()(implicit c: Connection): Unit = {
    SQL("SELECT lo_unlink(contents_oid) FROM upload").execute()
    SQL("TRUNCATE TABLE upload CASCADE").execute()
    SQL("TRUNCATE TABLE document_set CASCADE").execute()
    SQL("TRUNCATE TABLE \"user\" CASCADE").execute()
    SQL("INSERT INTO \"user\" (id, email, role, password_hash, confirmed_at, email_subscriber) VALUES (1, 'admin@overview-project.org', 2, '$2a$07$ZNI3MdA1MK7Td2w1EKpl5u38nll/MvlaRfZn0S8HLerNuP2hoD5JW', TIMESTAMP '1970-01-01 00:00:00', FALSE)").execute()
  }

  def insertCsvImportDocumentSet(uploadedFileId: Long)(implicit c: Connection): Long = {
    SQL("""
      INSERT INTO document_set (public, title, uploaded_file_id, created_at, document_count, document_processing_error_count, import_overflow_count, lang, supplied_stop_words, version)
      VALUES ('false', {title}, {uploadedFileId}, TIMESTAMP '1970-01-01 00:00:00', 100, 0, 0, 'en', '', {version})
      """).on(
      'title -> "Csv Import",
      'uploadedFileId -> uploadedFileId,
      'version -> DocumentSetVersion.current).executeInsert().getOrElse(failInsert)
  }

  def insertDocumentSet(query: String)(implicit c: Connection): Long = {
    SQL("""
      INSERT INTO document_set (public, title, query, created_at, document_count, document_processing_error_count, import_overflow_count, lang, supplied_stop_words, version)
      VALUES ('false', {title}, {query}, TIMESTAMP '1970-01-01 00:00:00', 100, 0, 0, 'en', '', {version})
      """).on(
      'title -> ("From query: " + query),
      'query -> query,
      'version -> DocumentSetVersion.current).executeInsert().getOrElse(failInsert)
  }

  def insertUploadedFile(contentDisposition: String, contentType: String, size: Long)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO uploaded_file (content_disposition, content_type, uploaded_at, size)
        VALUES ({contentDisposition}, {contentType}, TIMESTAMP '1970-01-01 00:00:00', {size})
        """).on(
      "contentDisposition" -> contentDisposition,
      "contentType" -> contentType,
      "size" -> size).executeInsert().getOrElse(failInsert)
  }

  def insertNode(documentSetId: Long, parentId: Option[Long], description: String)(implicit c: Connection): Long = {
    SQL("""
      INSERT INTO node (id, document_set_id, parent_id, description, is_leaf)
      VALUES ({id}, {document_set_id}, {parent_id}, {description}, FALSE)
      """).on(
      "id" -> nextNodeId(documentSetId),
      "document_set_id" -> documentSetId,
      "parent_id" -> parentId,
      "description" -> description).executeInsert().getOrElse(failInsert)
  }

  def insertDocument(documentSetId: Long, description: String, documentCloudId: String, title: Option[String] = None)(implicit connection: Connection): Long = {
    SQL("""
        INSERT INTO document (id, type, document_set_id, description, documentcloud_id, title)
        VALUES ({id}, 'DocumentCloudDocument'::document_type, {documentSetId}, {description}, {documentCloudId}, {title})
        """).on(
      "id" -> nextDocumentId(documentSetId),
      "documentSetId" -> documentSetId,
      "description" -> description,
      "documentCloudId" -> documentCloudId,
      "title" -> title).
      executeInsert().getOrElse(failInsert)
  }

  def insertNodeDocument(nodeId: Long, documentId: Long)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO node_document(node_id, document_id) VALUES ({nodeId}, {documentId})
        """).on("nodeId" -> nodeId, "documentId" -> documentId).
      executeInsert().getOrElse(failInsert)
  }

  def insertTag(documentSetId: Long, name: String)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO tag (name, document_set_id, color)
        VALUES ({name}, {documentSetId}, 'ffffff')
        """).on("name" -> name, "documentSetId" -> documentSetId).
      executeInsert().getOrElse(failInsert)
  }

  def insertDocumentWithNode(documentSetId: Long,
    description: String, documentCloudId: String, nodeId: Long)(implicit connection: Connection): Long = {
    val documentId = insertDocument(documentSetId, description, documentCloudId)
    insertNodeDocument(nodeId, documentId)

    documentId
  }

  def insertNodes(documentSetId: Long, count: Int)(implicit c: Connection): Seq[Long] = {
    for (i <- 1 to count) yield insertNode(documentSetId, None, "node-" + i)
  }

  def insertDocumentsForeachNode(documentSetId: Long, nodeIds: Seq[Long],
    documentCount: Int)(implicit c: Connection): Seq[Long] = {

    nodeIds.flatMap(n =>
      for (i <- 1 to documentCount) yield insertDocumentWithNode(documentSetId,
        "description-" + i, "documentcloudId-" + i, n))
  }

  def insertDocuments(documentSetId: Long, count: Int)(implicit c: Connection): Seq[Long] = {
    for (i <- 1 to count) yield insertDocument(documentSetId, "description-" + i, "documentCloudId-" + i)
  }

  def tagDocuments(tagId: Long, documentIds: Seq[Long])(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document_tag (document_id, tag_id)
        SELECT id, {tagId} FROM document
        WHERE id IN """ + documentIds.mkString("(", ",", ")")).on("tagId" -> tagId).executeUpdate()
  }
}
