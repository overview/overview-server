package models

import scala.collection.JavaConversions._
import anorm._
import anorm.SqlParser._
import java.sql.Connection
import play.api.Play.current
import play.api.db.DB


/**
 * Loads data from the database about subTrees
 */
class SubTreeLoader(rootId: Long, depth: Int, 
					loader: SubTreeDataLoader = new SubTreeDataLoader(),
					parser: SubTreeDataParser = new SubTreeDataParser()) {
  
  /**
   * @return a list of all the Nodes in the subTree
   */
  def loadNodes()(implicit connection : Connection) : Seq[core.Node] = {
	
    val nodeData = loader.loadNodeData(rootId, depth)
    val nodeIds = nodeData.map(_._1).distinct

    val documentData = loader.loadDocumentIds(nodeIds)
    val nodeTagCountData = loader.loadNodeTagCounts(nodeIds)
    
    parser.createNodes(nodeData, documentData, nodeTagCountData)
  }

  /**
   * @return a list of Documents whose ids are referenced by the passed in nodes. The list is sorted
   * by document IDs and all the elements are distinct, even if documentIds are included in multiple
   * Nodes.
   */
  def loadDocuments(nodes: Seq[core.Node])(implicit connection : Connection) : Seq[core.Document] = {
    val documentIds = nodes.flatMap(_.documentIds.firstIds)
    val documentData = loader.loadDocuments(documentIds.distinct.sorted)
    val documentTagData = loader.loadDocumentTags(documentIds)
    
    parser.createDocuments(documentData, documentTagData)
  }
  
  def loadTags(documentSetId: Long)(implicit connection: Connection) : Seq[core.Tag] = {
    val tagData = loader.loadTags(documentSetId)
    Nil
  }
}