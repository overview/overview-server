package com.overviewdocs.util

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

// Singleton Akka actor system object. One per process, managing all actors.
object WorkerActorSystem {
  def withActorSystem(f: ActorSystem => Unit) {
    val config = ConfigFactory.parseString("log-dead-letters-during-shutdown = on")
      .withFallback(ConfigFactory.load())
    val context = ActorSystem("WorkerActorSystem", config)

    try {
      f(context)
    } finally {
      context.shutdown
    }
  }
}
