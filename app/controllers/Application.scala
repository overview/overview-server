package controllers

import play.api.mvc.{Action,Controller}

object Application  extends Controller {
  
  def index() = Action {
    Ok(views.html.index("The Overview Project"))
  }

  
}
