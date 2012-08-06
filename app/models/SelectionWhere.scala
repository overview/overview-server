package models

object SelectionWhere {
  
  def apply(nodeIds: Seq[Long], documentIds: Seq[Long]) : Seq[Option[String]] = {
    Seq(nodeSelection(nodeIds),
        documentSelection(documentIds))
  }
    
  private def nodeSelection(nodeIds: Seq[Long]) : Option[String] = nodeIds match {
    case Nil => None
    case _ => Some("""
                   document.id IN 
	                 (SELECT document_id FROM node_document WHERE node_id IN """ + 
	                 idList(nodeIds) + ")"
	               )
  }
  
  private def documentSelection(documentIds: Seq[Long]) : Option[String] = documentIds match {
    case Nil => None
    case _ => Some("document.id IN " + idList(documentIds))
  }
  
  private def idList(idList: Seq[Long]) : String = {
    idList.mkString("(", ",", ")")
  }

  
}