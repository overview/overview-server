package utils

import play.api.mvc.{JavascriptLitteral,PathBindable,QueryStringBindable}
import java.net.URLDecoder
import java.util.UUID
import scala.util.control.Exception.catching

object Binders {
  type UUID = java.util.UUID

  /** Binds e.g. "abcdef01-2345-6798-9abc-def012345678" to a UUID */
  implicit def pathBindableUUID = new PathBindable[UUID] {
    def bind(key: String, value: String) = {
      try {
        Right(java.util.UUID.fromString(URLDecoder.decode(value, "utf-8")))
      } catch {
        case e: IllegalArgumentException => Left("Cannot parse parameter " + key + " as UUID: " + e.getMessage)
      }
    }
    def unbind(key: String, value: UUID) = value.toString
  }

  implicit def queryStringBindableUUID = new QueryStringBindable[UUID] {
    def bind(key: String, params: Map[String, Seq[String]]) = params.get(key).flatMap(_.headOption).map { s =>
      // No need to use URLDecoder since Netty does that
      try {
        Right(java.util.UUID.fromString(s))
      } catch {
        case e: IllegalArgumentException => Left("Cannot parse parameter " + key + " as UUID: " + e.getMessage)
      }
    }
    def unbind(key: String, value: UUID) = key + "=" + value.toString // it's ASCII, so no need to encode
  }

  implicit def uuidJavascriptLitteral /*sic */ = new JavascriptLitteral[UUID] {
    def to(value: UUID) = value.toString
  }
}
