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

  implicit object hexByteArrayFormat extends PathBindable[Array[Byte]] {
    override def bind(key: String, value: String): Either[String,Array[Byte]] = {
      val ret = value
        .replaceAll("[^0-9a-fA-F]", "")
        .sliding(2, 2)
        .filter(_.length == 2)
        .toArray
        .map(Integer.parseInt(_, 16).toByte)
      Right(ret)
    }

    override def unbind(key: String, value: Array[Byte]): String =  {
      value.map((b) => "%02x".format(b)).mkString
    }
  }
}
