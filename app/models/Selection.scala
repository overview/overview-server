package models

import scala.collection.Set
import scala.collection.mutable.HashSet
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConversions._

import com.avaje.ebean._

case class Selection(
    tree: Tree,
    nodeids: Set[Long] = new HashSet[Long](),
    tagids: Set[Long] = new HashSet[Long](),
    documentids: Set[Long] = new HashSet[Long]()) {

    // Returns selection documents [start, end)
    def findDocumentsSlice(start: Int, end: Int): Iterable[Document] = {
        val query = buildQuery()
            .setFirstRow(start)
            .setMaxRows(end - start)

        return query.findList()
    }

    def findDocumentCount(): Long = {
        val query = buildQuery()

        return query.findRowCount()
    }

    private

    def buildQuery() : Query[Document] = {
        val wheres = new ArrayBuffer[String]()

        if (!nodeids.isEmpty()) {
            wheres += "document.id IN (SELECT document_id FROM node_document WHERE node_id IN (" + nodeids.mkString(",") + "))"
        }
        if (!tagids.isEmpty()) {
            wheres += "document.id IN (SELECT document_id FROM document_tag WHERE tag_id IN (" + tagids.mkString(",") + "))"
        }
        if (!documentids.isEmpty()) {
            wheres += "document.id IN (" + documentids.mkString(",") + ")"
        }

        val sql = new StringBuilder("SELECT id, title, text_url, view_url FROM document")
        if (!wheres.isEmpty()) {
            sql.append(" WHERE ").append(wheres.mkString(" AND "))
        }

        val rawSql = RawSqlBuilder.parse(sql.toString())
            .columnMapping("id", "id")
            .columnMapping("title", "title")
            .columnMapping("text_url", "textUrl")
            .columnMapping("view_url", "viewUrl")
            .create()

        val query = Ebean.find(classOf[Document])
            .setRawSql(rawSql)
            .orderBy("title, id")

        return query
    }
}
