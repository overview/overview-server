package com.overviewdocs.models

case class PdfNote(
  val pageIndex: Int,
  val x: Double,
  val y: Double,
  val width: Double,
  val height: Double,
  val text: String
)
