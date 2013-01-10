/*
 * Document.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */
package models.core

case class Document (id: Long, description: String, documentCloudId: Option[String], tags: Seq[Long], nodes: Seq[Long])

