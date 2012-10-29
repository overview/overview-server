package views

import play.api.i18n.{Lang,Messages}
import play.api.templates.Html
import play.api.Play
import play.api.Play.current

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

  private def basePathToMinifiedPath(basePath: String, extension: String) : String = {
    import java.security.MessageDigest
    import javax.xml.bind.annotation.adapters.HexBinaryAdapter

    val resource : java.net.URL = Play.resource("/public/" + basePath + extension).get

    val source = io.Source.fromURL(resource, "utf-8")
    val contents = source.mkString
    source.close
    val md5 = MessageDigest.getInstance("MD5")
    val md5bytes = md5.digest(contents.getBytes)
    val hash = (new HexBinaryAdapter).marshal(md5bytes).toLowerCase()

    basePath + "-" + hash + ".min" + extension
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

  def bundleStylesheet(bundleKey: String) : Html = {
    val path = bundlePath("stylesheets", bundleKey, ".css")
    Html(<link rel="stylesheet" href={path} />.toString)
  }
}
