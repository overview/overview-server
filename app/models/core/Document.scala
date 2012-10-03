/*
 * Document.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */
package models.core

case class Document (id: Long, title: String,  documentCloudId: String, tags: Seq[Long], nodes: Seq[Long])

