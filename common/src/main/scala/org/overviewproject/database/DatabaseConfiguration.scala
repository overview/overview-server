/*
 * DatabaseConfiguration.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, June 2012
 */

package org.overviewproject.database

import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariConfig
import java.util.Properties
import scala.collection.JavaConversions.asScalaSet

object DatabaseConfiguration {
  /** Reads db.default.url from application.conf. */
  lazy val fromConfig: HikariConfig = {
    val config = ConfigFactory.load.getConfig("db").getConfig("default")
    val props = new Properties()
    val entrySet = asScalaSet(config.entrySet)
    entrySet.foreach { entry => props.setProperty(entry.getKey, entry.getValue.unwrapped.toString) }
    new HikariConfig(props)
  }
}
