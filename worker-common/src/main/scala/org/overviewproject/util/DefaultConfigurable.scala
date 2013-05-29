package org.overviewproject.util

import scala.util.control.Exception.{Catch, failAsValue}

import com.typesafe.config.Config
import com.typesafe.config.ConfigException.{Missing, WrongType}



trait DefaultConfigurable {
  val configuration: Config
  val prefix: String
  val defaultValues: Map[String, Any]
  
  def fallbackOnDefault[T](name: String): Catch[T] =
    failAsValue(classOf[Missing], classOf[WrongType])(defaultValues(name).asInstanceOf[T])
    
  def getInt(name: String): Int = fallbackOnDefault(name) { configuration.getInt(path(prefix, name)) }
  def getString(name: String): String = fallbackOnDefault(name) { configuration.getString(path(prefix, name)) }
  
  private def path(prefix: String, name: String): String = 
    if (prefix.isEmpty) name
    else s"$prefix.$name"
}
