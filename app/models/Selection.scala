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
        val sql = buildSql("id, title, text_url, view_url")

        val rawSql = RawSqlBuilder.parse(sql)
            .columnMapping("id", "id")
            .columnMapping("title", "title")
            .columnMapping("text_url", "textUrl")
            .columnMapping("view_url", "viewUrl")
            .create()

        val query = Ebean.find(classOf[Document])
            .setRawSql(rawSql)
            .orderBy("title, id")
            .setFirstRow(start)
            .setMaxRows(end - start)

        return query.findList()
    }

    def findDocumentCount(): Long = {
        val sql = buildSql("COUNT(*) AS c")

        val query = Ebean.createSqlQuery(sql)

        val row = query.findUnique()

        return row.getInteger("c").longValue();
    }

    private

    def buildSql(whatToSelect: String) : String = {
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

        val sql = new StringBuilder("SELECT " + whatToSelect + " FROM document")

        if (!wheres.isEmpty()) {
            sql.append(" WHERE ").append(wheres.mkString(" AND "))
        }

        return sql.toString()
    }
}

