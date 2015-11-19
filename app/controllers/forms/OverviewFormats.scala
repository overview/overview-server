package controllers.forms

import com.fasterxml.jackson.core.JsonProcessingException
import play.api.data.FormError
import play.api.data.format.{Formatter,Formats}
import play.api.libs.json.{JsObject,Json}
import scala.util.{Failure,Success,Try}

object OverviewFormats {
  /** Parses a JsObject from a String, or fails with
    * "error.invalidMetadataJson".
    */
  def metadataJson: Formatter[JsObject] = new Formatter[JsObject] {
    private def err(key: String) = Seq(FormError(key, "error.invalidMetadataJson", Nil))

    override def bind(key: String, data: Map[String,String]): Either[Seq[FormError], JsObject] = {
      Formats.stringFormat.bind(key, data)
        .right.flatMap(s => {
          Try(Json.parse(s)) match {
            case Success(json) => json.asOpt[JsObject].toRight(err(key))
            case Failure(_: JsonProcessingException) => Left(err(key))
            case Failure(ex) => throw ex
          }
        })
    }

    override def unbind(key: String, value: JsObject) = Map(key -> value.toString)
  }
}
