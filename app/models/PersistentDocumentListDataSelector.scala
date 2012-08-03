package models

class PersistentDocumentListSelector {

  protected def createWhereClause(nodeIds: Seq[Long], documentIds: Seq[Long]): String = {
    val whereClauses = List(
    		whereClauseForIds(nodeSelection(nodeIds), nodeIds),
    		whereClauseForIds(documentSelection(documentIds), documentIds)
    )    

    combineWhereClauses(whereClauses)
  }
  
  private def nodeSelection(nodeIds: Seq[Long]) : String = {
    """
    document.id IN 
	  (SELECT document_id FROM node_document WHERE node_id IN """ + idList(nodeIds) + ")"
  }
  
  private def documentSelection(documentIds: Seq[Long]) : String = {
    "document.id IN " + idList(documentIds)
  }
  
  private def combineWhereClauses(whereClauses: List[Option[String]]) : String = {
    val actualWheres = whereClauses.flatMap(_.toList)
    actualWheres match {
      case Nil => ""
      case _ => actualWheres.mkString("WHERE ", " AND ", " ")
    }
  }
  
  private def whereClauseForIds(where: String, ids: Seq[Long]) : Option[String] =
    ids match {
    case Nil => None
    case _ => Some(where)
  }

  private def idList(idList: Seq[Long]) : String = {
    idList.mkString("(", ",", ")")
  }

}