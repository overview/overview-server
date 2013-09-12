package org.overviewproject.test

import akka.actor._

class ForwardingActor(target: ActorRef) extends Actor {
  def receive = {
    case msg => target forward msg
  }
  
  override def postStop = target ! PoisonPill
}

object ForwardingActor {
  def apply(target: ActorRef): Props = Props(new ForwardingActor(target))
}