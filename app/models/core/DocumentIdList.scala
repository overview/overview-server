/*
 * DocumentIdList.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */

package models.core

/**
 * A list of the first N (defined by caller) document Ids in some set of documents,
 * and the total number of documents in the set.
 */
case class DocumentIdList (firstIds: Seq[Long], totalCount: Long)
