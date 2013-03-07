package org.overviewproject.clone

import anorm._
import org.overviewproject.persistence.DocumentSetIdGenerator
import org.overviewproject.tree.orm.Node
import java.sql.Connection
import org.overviewproject.database.Database

object NodeCloner {
  private val DocumentSetIdMask: Long = 0x00000000FFFFFFFFl
  
  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long, documentIdMapping: Map[Long, Long]): Map[Long, Long] = {
    import org.overviewproject.persistence.orm.Schema
    import org.overviewproject.postgres.SquerylEntrypoint._

    val ids = new DocumentSetIdGenerator(cloneDocumentSetId)

    def cloneSubTree(sourceParentNode: Node, nodes: Iterable[Node], nodeIds: Map[Long, Long]): Map[Long, Long] = {
      val cloneParentId = sourceParentNode.parentId.flatMap(nodeIds.get(_))

      val cloneCachedDocumentIds = sourceParentNode.cachedDocumentIds.flatMap(d => documentIdMapping.get(d))
      val cloneParentNode = sourceParentNode.copy(id = ids.next, documentSetId = cloneDocumentSetId, parentId = cloneParentId,
        cachedDocumentIds = cloneCachedDocumentIds)

      Schema.nodes.insert(cloneParentNode)

      val parentIdMap = Map(sourceParentNode.id -> cloneParentNode.id)

      val (childNodes, subTreeNodes) = nodes.partition(_.parentId.exists(_ == sourceParentNode.id))

      val subTreeMaps = childNodes.map(cloneSubTree(_, subTreeNodes, parentIdMap))

      subTreeMaps.fold(parentIdMap)(_ ++ _)
    }

    val sourceNodes = Schema.nodes.where(n => n.documentSetId === sourceDocumentSetId)

    val (rootNode, subTreeNodes) = sourceNodes.partition(_.parentId.isEmpty)
    rootNode.headOption.map { cloneSubTree(_, subTreeNodes, Map()) }.getOrElse(Map.empty)
  }

  def dbClone(sourceDocumentSetId: Long, cloneDocumentSetId: Long): Boolean = {
    implicit val c: Connection = Database.currentConnection  
  
    SQL("""
        WITH 
          cached_document_ids AS 
            (SELECT id AS node_id, unnest(cached_document_ids) AS document_id FROM node WHERE document_set_id = {sourceDocumentSetId}),
          cloned_document_ids AS
            (SELECT node_id, ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & document_id) AS clone_id FROM cached_document_ids),
          cloned_cache AS
            (SELECT node_id, array_agg(clone_id) AS cached_document_ids FROM cloned_document_ids GROUP BY node_id)

        INSERT INTO node (id, document_set_id, parent_id, description, cached_document_ids, cached_size)
          SELECT
            ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & id) AS clone_id,
            {cloneDocumentSetId},
            ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & parent_id) AS clone_parent_id,
            description,
            cloned_cache.cached_document_ids,
            cached_size
          FROM node, cloned_cache WHERE document_set_id = {sourceDocumentSetId} AND node.id = node_id
        """).on("cloneDocumentSetId" -> cloneDocumentSetId,
            "sourceDocumentSetId" -> sourceDocumentSetId,
            "documentSetIdMask" -> DocumentSetIdMask).execute
  }

}