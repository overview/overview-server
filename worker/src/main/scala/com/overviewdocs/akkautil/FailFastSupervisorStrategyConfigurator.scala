package com.overviewdocs.akkautil

import akka.actor.{AllForOneStrategy,SupervisorStrategyConfigurator}
import akka.actor.SupervisorStrategy.Escalate

class FailFastSupervisorStrategyConfigurator extends SupervisorStrategyConfigurator {
  override def create = AllForOneStrategy(0) {
    case _ => Escalate
  }
}
