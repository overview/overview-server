package controllers

import java.util.UUID
import play.api.mvc.{Controller, Request, RequestHeader, Result}

trait MassUploadController extends Controller {
  def create(guid: UUID) = TODO
}

object MassUploadController extends MassUploadController
