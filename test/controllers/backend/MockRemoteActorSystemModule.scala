package controllers.backend

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import akka.util.Timeout

import modules.RemoteActorSystemModule

class MockRemoteActorSystemModule(val actorSystem: ActorSystem) extends RemoteActorSystemModule {
  override val defaultTimeout = Timeout(3, java.util.concurrent.TimeUnit.SECONDS)
  val mockBroker = TestProbe("messageBroker")(actorSystem)
  val mockBrokerRef = mockBroker.ref
  override def messageBroker = actorSystem.actorSelection(mockBrokerRef.path)
}
