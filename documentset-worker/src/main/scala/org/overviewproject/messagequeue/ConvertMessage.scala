package org.overviewproject.messagequeue

import play.api.libs.json._
import play.api.libs.functional.syntax.functionalCanBuildApplicative
import play.api.libs.functional.syntax.toFunctionalBuilderOps


trait ConvertMessage {
  protected case class Message(cmd: String, args: JsValue)
  
  implicit private val messageReads = Json.reads[Message]
  
  protected def getMessage(message: String): Message = Json.parse(message).as[Message]
}

