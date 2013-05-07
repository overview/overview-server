package org.overviewproject.documentcloud

import akka.actor._

// Start putting actor creation in factories, because akka will soon deprecate
// using closures.
trait RetrieverFactory {
  def produce(document: Document, receiver: ActorRef): Actor
}