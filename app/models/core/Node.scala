package models.core

case class Node (id: Long, 
                 description: String,
                 childNodeIds: Seq[Long],
                 documentIds: DocumentIdList,
                 tagCounts:Seq[(Long, Long)] = Nil)
				 