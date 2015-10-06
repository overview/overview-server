package com.overviewdocs.test

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender,TestKit,TestKitBase}
import akka.util.Timeout
import org.specs2.specification.Scope
import org.specs2.mutable.After

trait ActorSystemContext
  extends Scope
  with TestKitBase
  with ImplicitSender
  with After
{
  implicit lazy val system: ActorSystem = ActorSystem()
  implicit val timeout: Timeout = Timeout.longToTimeout(999999999L)

  override def after = TestKit.shutdownActorSystem(system)
}
