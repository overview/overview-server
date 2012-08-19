/**
 * Logger.scala
 * 
 * Worker logging singleton object. Pass-through to LogBack
 * 
 * Overview Project, created August 2012
 * @author Jonathan Stray
 * 
 */

package logging

import org.slf4j.LoggerFactory

object Logger {
   private def logger = LoggerFactory.getLogger("WORKER")

   def trace(msg:String) = logger.trace(msg)
   def debug(msg:String) = logger.debug(msg)
   def info(msg:String) = logger.info(msg)
   def warn(msg:String) = logger.warn(msg)
   def error(msg:String) = logger.error(msg)   
}