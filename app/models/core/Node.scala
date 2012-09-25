/*
 * Node.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */

package models.core

/**
 * A node in the tree of nodes partitioning the document set.
 * @param documentIds the first 10 document ids contained by the Node. A Node contains all the document ids of its child nodes.
 * @param tagCounts maps tag ids to the number of documents in the Node with the tag. The id is in String form as a convenience to the client view.
 */
case class Node (id: Long,
                 description: String,
                 childNodeIds: Seq[Long],
                 documentIds: DocumentIdList,
                 tagCounts:Map[String, Long])
