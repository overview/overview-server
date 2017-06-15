package controllers.api

import controllers.ControllerHelpers
import play.api.mvc.{Controller=>PlayController}

/** API controllers are a bit different from regular ones:
  *
  * * There's always a User
  * * There's never i18n
  */
trait ApiController extends PlayController with ControllerHelpers
