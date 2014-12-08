package org.overviewproject.models

case class Page(
    id: Long,
    fileId: Long,
    pageNumber: Int,
    referenceCount: Int,
    dataLocation: String,
    dataSize: Long,
    data: Option[Array[Byte]],
    text: Option[String],
    dataErrorMessage: Option[String] = None,
    textErrorMessage: Option[String] = None)
