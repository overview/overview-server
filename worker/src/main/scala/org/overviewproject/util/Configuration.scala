package org.overviewproject.util

import scala.util.control.Exception._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigException.WrongType

object Configuration {
  private val MaxDocuments: String = "max_documents"

  private val configuration: Config = ConfigFactory.load()
  
  private val defaultInts: Map[String, Int] = Map(
      (MaxDocuments -> 20000)
  )

 private def fallBackOnDefault(name: String): Catch[Int] = 
   failAsValue(classOf[Missing], classOf[WrongType])(defaultInts(name))

 val maxDocuments = fallBackOnDefault(MaxDocuments) { configuration.getInt(MaxDocuments) }

}