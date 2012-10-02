/*
 * Tag.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, August 2012
 */

package models.core

case class Tag(id: Long, name: String, color: Option[String], documentIds: DocumentIdList)
