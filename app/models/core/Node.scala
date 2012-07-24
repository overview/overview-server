package models.core

case class Node (id: Long, 
                 description: String,
                 childNodeIds: List[Long],
                 documentIds: List[Long])
				 