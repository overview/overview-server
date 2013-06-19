package org.overviewproject.util

import scala.collection.immutable.Vector

/** The colors we use for tagging.
  *
  * This list of colors has a prime number of entries. Each entry must be
  * reasonably distinct from the others. To generate an automatic color for a
  * tag with name "name", we use Java's name.hashCode mod nColors.
  *
  * Java's String.hashCode() for string s returns
  * s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1].
  *
  * Note: this list is duplicated in CoffeeScript.
  */
object TagColorList {
  val colors = Vector[String](
    "ff0009",
    "ff7700",
    "fff700",
    //"89ff00", # commented out for a prime number of colors for good distribution
    "09ff00",
    "00ff77",
    "00fff7",
    "0089ff",
    "0009ff",
    "7700ff",
    "f700ff",
    "ff0089",
    "ff7378",
    "ffb573",
    "fffb73",
    "beff73",
    "78ff73",
    "73ffb5",
    "73fffb",
    "73beff",
    "7378ff",
    "b573ff",
    "fb73ff",
    "ff73be"
  )

  def forString(s: String) : String = {
    val hash = s.hashCode
    val index = ((hash % colors.length) + colors.length) % colors.length
    colors(index)
  }
}
