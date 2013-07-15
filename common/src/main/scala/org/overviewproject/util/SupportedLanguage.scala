package org.overviewproject.util

import java.util.Locale

case class SupportedLanguage(val languageCode: String) {
  val locale : Locale = new Locale(languageCode)

  def displayLanguage(implicit callerLocale: Locale) : String = locale.getDisplayLanguage(callerLocale)
}
