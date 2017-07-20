package controllers.api

import play.api.http.HeaderNames
import play.api.mvc.Results

trait ApiControllerHelpers extends Results with HeaderNames with controllers.ControllerHelpers
