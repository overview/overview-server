package org.overviewproject.util

import scala.util.control.Exception._
import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigException.WrongType

object Configuration {
  private val MaxDocuments: String = "max_documents"
  private val MaxInFlightRequests: String = "max_inflight_requests"
  private val PageSize: String = "page_size"

  private val configuration: Config = ConfigFactory.load()

  private val defaultInts: Map[String, Int] = Map(
    (MaxDocuments -> 50000),
    (PageSize -> 50),
    (MaxInFlightRequests -> 4))

  private def fallBackOnDefault(name: String): Catch[Int] =
    failAsValue(classOf[Missing], classOf[WrongType])(defaultInts(name))

  private def getInt(name: String): Int = fallBackOnDefault(name) { configuration.getInt(name) }

  val maxDocuments: Int = getInt(MaxDocuments)
  val pageSize: Int = getInt(PageSize)
  val maxInFlightRequests: Int = getInt(MaxInFlightRequests)
}