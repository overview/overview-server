package models.odf

sealed trait OdfInternalFile

case class OdsSpreadsheet(headers: Iterable[String], rows: Iterable[Iterable[Any]]) extends OdfInternalFile
