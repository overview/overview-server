package extensions

import play.api.mvc.PathBindable

import models.export.format._

object Binders {
  implicit object bindableExportFormat extends PathBindable[models.export.format.Format] {
    override def bind(key: String, value: String) : Either[String, Format] = value match {
      case "csv" => Right(CsvFormat)
      case "ods" => Right(OdsFormat)
      case "xlsx" => Right(XlsxFormat)
      case _ => Left("Cannot parse export format `" + value + "`")
    }

    override def unbind(key: String, value: Format) : String = value match {
      case CsvFormat => "csv"
      case OdsFormat => "ods"
      case XlsxFormat => "xlsx"
      case _ => "???" // TODO make Format a sealed trait
    }
  }
}
