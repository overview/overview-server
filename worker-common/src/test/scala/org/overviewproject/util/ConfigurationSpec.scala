package org.overviewproject.util

import scala.collection.JavaConverters._
import org.specs2.mutable.Specification
import com.typesafe.config.{Config, ConfigFactory}

class ConfigurationSpec extends Specification {

  // NOTE: this line does not seem to load worker-conf/application.conf
  // This file tests default values, not any value actually from the conf file
  // jms 2013-10-14
  val playConfig = ConfigFactory.load()

  "Configuration" should {

    class SubKeys(config:Config) extends ConfigurationKeys(config) {
      override def path: Option[String] = Some("path")
      override def keys: Map[String, Any] = Map(
        "sub" -> "subvalue")
    }

    object RootKeys extends ConfigurationKeys(playConfig) {
      override def keys: Map[String, Any] = Map(
        "str" -> "value",
        "int" -> 5)

      val subKeys = new SubKeys(playConfig)
    }

    "read default values" in {
      RootKeys.getString("str") must be equalTo ("value")
      RootKeys.getInt("int") must be equalTo (5)
    }

    "can't read undefined keys" in {
      RootKeys.getString("foo") must throwA[IllegalArgumentException]
    }

    "test type safety" in 
    {
      RootKeys.getInt("str") must throwA[IllegalArgumentException]
    }

    "read values in sub path" in {
      RootKeys.subKeys.getString("sub") must be equalTo ("subvalue")
    }

    "have MaxDocuments value" in {
      Configuration.getInt("max_documents") must be equalTo (50000)
    }
/*
    "have BrokerUri value" in {

      Configuration.messageQueue.getString("broker_uri") must be equalTo ("tcp://localhost:61613")
    }
*/
    "have pageSize value" in {
      Configuration.getInt("page_size") must be equalTo (50)
    }

    "have maxInFlightRequests value" in {
      Configuration.getInt("max_inflight_requests") must be equalTo (4)
    }

  }
}
