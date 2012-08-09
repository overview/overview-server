/*
 * Document.scala 
 * 
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */
package models.core

case class Document (id: Long, title: String, textUrl: String, viewUrl: String,
		             tags: Seq[Long])
