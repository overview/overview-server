package org.overviewproject.test

import org.specs2.mutable.After

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}

abstract class ActorSystemContext extends TestKit(ActorSystem()) with ImplicitSender with After {
  def after = system.shutdown()
}
