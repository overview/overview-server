package views.html.helper

import play.api.i18n.Lang

object DocumentProcessingErrorDisplay {
  private val UrlDisplayLength: Int = 65
  private val Ellipsis: String = "\u2026"
  
  def url(textUrl: String) = 
    if (textUrl.length <= UrlDisplayLength) textUrl
    else Ellipsis + textUrl.takeRight(UrlDisplayLength)
}