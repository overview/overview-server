package views.html.helper

import play.api.i18n.Lang

object DocumentProcessingErrorDisplay {
  private val m = views.ScopedMessages("views.DocumentProcessingError.error.td")
  private val UrlDisplayLength: Int = 40
  private val Ellipsis: String = "\u2026"
    
  def status(statusCode: Option[Int])(implicit lang: Lang): String = {
    statusCode match {
      case Some(403) => m("access_denied")
      case Some(404) => m("not_found")
      case Some(_) => m("server_error")
      case _ => m("internal_error")
    }
  }
  
  def url(textUrl: String) = 
    if (textUrl.length <= UrlDisplayLength) textUrl
    else Ellipsis + textUrl.takeRight(UrlDisplayLength)
}