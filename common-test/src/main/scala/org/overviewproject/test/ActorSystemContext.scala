package org.overviewproject.test

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender,TestKit,TestKitBase}
import org.specs2.mutable.After

trait ActorSystemContext
  extends TestKitBase
  with ImplicitSender
  with After
{
  implicit lazy val system = ActorSystem()

  override def after = TestKit.shutdownActorSystem(system)
}
