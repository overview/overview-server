package models

object SelectionWhere {
  
  def apply(nodeIds: Seq[Long], tagIds: Seq[Long], documentIds: Seq[Long]) :
	  Seq[Option[String]] = {
    
    Seq(nodeSelection(nodeIds),
        tagSelection(tagIds),
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
  
  private def tagSelection(tagIds: Seq[Long]) : Option[String] = tagIds match {
    case Nil => None
    case _ => Some("""
    			   document.id IN 
                     (SELECT document_id FROM document_tag WHERE tag_id IN """ +
                     idList(tagIds) + ")"
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