package org.overviewproject.postgres

import java.net.{InetAddress=>JInetAddress}

/** A stupid wrapper around java.util.InetAddress, for Squeryl.
  *
  * See
  * https://groups.google.com/forum/#!searchin/squeryl/NonPrimitiveJdbcMapper%7Csort:relevance%7Cspell:false/squeryl/RaTLokGv2J4/zgq8wDofTcMJ
  *
  * In brief: InetAddress is a parent class; any sample value will be of a more
  * specific class. That means Squeryl can't register a NonPrimitiveJdbcMapper
  * for the generic type. This class gets around it by being a specific type.
  *
  * It isn't all that painful. We just need to wrap all the functionality from
  * java.net.InetAddress that we will ever use. Luckily, that isn't much (yet).
  */
class InetAddress(val wrapped: JInetAddress) {
  override def equals(o: Any) : Boolean = wrapped.equals(o)
  def getHostAddress: String = wrapped.getHostAddress
}

object InetAddress {
  def getByName(host: String) : InetAddress = new InetAddress(JInetAddress.getByName(host))
}
