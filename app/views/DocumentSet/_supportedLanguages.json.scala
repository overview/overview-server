package views.DocumentSet

import java.util.Locale
import play.api.i18n.Lang
import play.api.libs.json.{Json,JsValue}

import org.overviewproject.util.{SupportedLanguages,SupportedLanguage}

/** Returns [ { code: "en", name: "English" }, ... ]
  *
  * @see org.overviewproject.util.SupportedLanguages
  **/
object _supportedLanguages {
  private[DocumentSet] def supportedLanguageToJson(language: SupportedLanguage, locale: Locale) : JsValue = {
    Json.obj(
      "code" -> language.languageCode,
      "name" -> language.displayLanguage(locale)
    )
  }

  def apply()(implicit lang: Lang) : JsValue = {
    val locale = lang.toLocale
    val languages = SupportedLanguages.languagesSortedInCallerLocale(locale)
      .map(supportedLanguageToJson(_, locale))
    Json.toJson(languages)
  }
}
