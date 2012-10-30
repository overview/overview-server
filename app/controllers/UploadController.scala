package controllers

import java.util.UUID
import play.api.mvc.{Action,BodyParsers}

object UploadController extends BaseController {
  def show(uuid: UUID) = Action(BodyParsers.parse.anyContent) { request =>
    Ok("ok")
  }

  def create(uuid: UUID) = Action(BodyParsers.parse.anyContent) { request =>
    Ok("ok")
  }
}
