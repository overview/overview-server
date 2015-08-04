package com.overviewdocs.tree.orm

import org.squeryl.KeyedEntity

case class Page(
    fileId: Long,
    pageNumber: Int,
    dataLocation: Option[String],
    dataSize: Long,
    data: Option[Array[Byte]],
    text: Option[String],
    dataErrorMessage: Option[String] = None,
    textErrorMessage: Option[String] = None,
    id: Long = 0L) extends KeyedEntity[Long] {

  override def isPersisted: Boolean = id != 0L
}
