package com.overviewdocs.test

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer,Materializer}
import akka.testkit.{ImplicitSender,TestKit,TestKitBase}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.specs2.specification.Scope

trait ActorSystemContext
  extends Scope
  with TestKitBase
  with ImplicitSender
{
  // We use a single ActorSystem for all tests, to speed things up.
  implicit lazy val system: ActorSystem = ActorSystemContext.singletonActorSystem
  implicit lazy val materializer: Materializer = ActorMaterializer.create(system)
  implicit val timeout: Timeout = Timeout(21474835000L, java.util.concurrent.TimeUnit.MILLISECONDS)
}

object ActorSystemContext {
  lazy val singletonActorSystem: ActorSystem = ActorSystem(
    name="ActorSystemContext",
    config=ConfigFactory.parseString("""
      akka {
        log-dead-letters: off

        actor {
          provider: "akka.actor.LocalActorRefProvider"
        }

        remote {
          enabled-transports: []
        }

        default-dispatcher {
          type = Dispatcher
          executor = "fork-join-executor"
          fork-join-executor {
            paralellism-min = 3
            paralellism-max = 10
          }
        }
      }
    """)
  )
}
