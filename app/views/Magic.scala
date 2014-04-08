package views

import java.util.Locale
import javax.xml.bind.annotation.adapters.HexBinaryAdapter
import play.api.i18n.{Lang,Messages}
import play.api.Play
import play.api.Play.current
import play.api.templates.Html


/**
 * A convenience class on top of Messages.
 *
 * Usage:
 *
 *     val m = ScopedMessages("toplevel.key")
 *     val message = m("subkey")
 *
 * This is equivalent to:
 *
 *     val message = Messages("toplevel.key.subkey")
 */
case class ScopedMessages(scope: String) {
  /**
   * @return a translated message for the given sub-key
   */
  def apply(key: String, args: Any*)(implicit lang: Lang) : String = {
    Messages(scope + "." + key, args : _*)
  }

  /**
   * @return a translated message for the given sub-key, or None if the
   *         key isn't translated.
   */
  def optional(key: String, args: Any*)(implicit lang: Lang) : Option[String] = {
    val ret = apply(key, args)
    if (ret == scope + "." + key) {
      None
    } else {
      Some(ret)
    }
  }
}

/*
 * Functions that every template can access.
 */
object Magic {
  val t = play.api.i18n.Messages
  val scopedMessages = ScopedMessages

  private def streamToHash(stream: java.io.InputStream) : String = {
    import java.security.MessageDigest

    val md5 = MessageDigest.getInstance("MD5")
    val digestStream = new java.security.DigestInputStream(stream, md5)

    // Read entire file and ignore its contents (updates md5)
    val uselessByteArray = new Array[Byte](10240)
    while (-1 != digestStream.read(uselessByteArray)) {}

    (new HexBinaryAdapter).marshal(md5.digest).toLowerCase()
  }

  private def basePathToMinifiedPath(basePath: String, extension: String) : String = {
    val stream = Play.resourceAsStream("/public/" + basePath + extension).get

    try {
      val hash = streamToHash(stream)
      basePath + "-" + hash + ".min" + extension
    } finally {
      stream.close
    }
  }

  private def bundlePath(bundleType: String, bundleKey: String, extension: String) : String = {
    val basePath = bundleType + "/" + bundleKey

    val path = if (Play.configuration.getBoolean("assets.compress").getOrElse(false)) {
      basePathToMinifiedPath(basePath, extension)
    } else {
      basePath + extension
    }

    "/assets/" + path
  }

  def bundleJavascript(bundleKey: String) : Html = {
    val path = bundlePath("javascripts", bundleKey, ".js")
    Html(<script type="text/javascript" src={path}></script>.toString)
  }

  /** Lossy filename escape: takes a String and returns a similar String.
   *
   * "?" will be replaced with "_", so we can include the character in URLs.
   */
  def escapeFilename(filename: String) = {
    val InvalidCharRegex = """[\x00-\x1f\\\?/%*:|"<>]""".r
    InvalidCharRegex.replaceAllIn(filename, "_")
  }

  /** Returns "French" when code is "fr", for instance. */
  def displayLanguageCode(code: String)(implicit lang: Lang) = {
    val locale = Locale.forLanguageTag(code)
    locale.getDisplayLanguage(lang.toLocale)
  }
}
