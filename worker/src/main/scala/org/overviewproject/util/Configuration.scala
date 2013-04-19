package org.overviewproject.util

import scala.util.control.Exception._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigException.WrongType

object Configuration {
  private val MaxDocuments: String = "max_documents"
  private val PageSize: String = "page_size"
    
  private val configuration: Config = ConfigFactory.load()
  
  private val defaultInts: Map[String, Int] = Map(
      (MaxDocuments -> 20000),
      (PageSize -> 50)
  )

 private def fallBackOnDefault(name: String): Catch[Int] = 
   failAsValue(classOf[Missing], classOf[WrongType])(defaultInts(name))

 val maxDocuments: Int = fallBackOnDefault(MaxDocuments) { configuration.getInt(MaxDocuments) }
 val pageSize: Int = fallBackOnDefault(PageSize) { configuration.getInt(PageSize) }
}