package org.overviewproject.util

import scala.collection.JavaConverters._
import org.specs2.mutable.Specification
import com.typesafe.config.ConfigFactory

class ConfigurationSpec extends Specification {

  "Configuration" should {
    object RootDefaults extends ConfigurationDefault {
      override def path: Option[String] = None
      override def defaultValues: Map[String, Any] = Map(
        "str" -> "value",
        "int" -> 5)
    }

    object SubDefaults extends ConfigurationDefault {
      override def path: Option[String] = Some("path")
      override def defaultValues: Map[String, Any] = Map(
        "sub" -> "subvalue")
    }

    object TestConfiguration extends ConfigurationWithDefaults {
      override val defaultConfigs = Seq(RootDefaults, SubDefaults)

      val configuration = createWithDefaults
    }

    "have BrokerUri value" in {

      Configuration.messageQueue.brokerUri must be equalTo ("tcp://localhost:61613")
    }

    "have MaxDocuments value" in {
      Configuration.maxDocuments must be equalTo (50000)
    }

    "have pageSize value" in {
      Configuration.pageSize must be equalTo (50)
    }

    "have maxInFlightRequests value" in {
      Configuration.maxInFlightRequests must be equalTo (4)
    }

    "read default values" in {
      TestConfiguration.configuration.getString("str") must be equalTo ("value")
      TestConfiguration.configuration.getInt("int") must be equalTo (5)
    }

    "read values in sub path" in {
      TestConfiguration.configuration.getString("path.sub") must be equalTo ("subvalue")
    }
  }
}
