package org.overviewproject.jobhandler

import play.api.libs.json._


trait ConvertMessage {
  protected case class Message(cmd: String, args: JsValue)
  
  implicit private val messageReads = Json.reads[Message]
  
  protected def getMessage(message: String): Message = Json.parse(message).as[Message]
}

