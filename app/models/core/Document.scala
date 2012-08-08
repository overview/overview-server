package models.core

case class Document (id: Long, title: String, textUrl: String, viewUrl: String,
		             tags: Seq[Long] = Nil)
