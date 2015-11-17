package com.overviewdocs.util

import com.typesafe.config.{Config,ConfigFactory}

trait Configuration {
  protected val config: Config

  def getString(key: String): String = config.getString(key)
  def getInt(key: String): Int = config.getInt(key)
}

// Root configuration object reads keys at root level, and points to sub-paths
object Configuration extends Configuration {
  override protected val config = ConfigFactory.load
}
