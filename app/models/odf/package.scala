package models.odf

sealed trait OdfInternalFile

case class OdsSpreadsheet(headers: Product, rows: Iterable[Product]) extends OdfInternalFile
