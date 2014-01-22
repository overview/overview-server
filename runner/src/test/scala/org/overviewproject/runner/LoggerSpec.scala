package org.overviewproject.runner

import java.io.ByteArrayOutputStream
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.sys.process.ProcessLogger

class LoggerSpec extends Specification {
  trait Base extends Scope {
    val outStream = new ByteArrayOutputStream()
    val errStream = new ByteArrayOutputStream()
    val logger = new Logger(outStream, errStream)
  }

  "Logger" should {
    "log things" in new Base {
      logger.out.println("foo")
      outStream.toByteArray must beEqualTo("foo\n".getBytes())
    }

    "prefix log entries" in new Base {
      logger.sublogger("source", None).out.println("foo")
      outStream.toByteArray must beEqualTo("[source] foo\n".getBytes())
    }

    "prefix every line of log entries" in new Base {
      logger.sublogger("source", None).out.println("foo\nfoo")
      outStream.toByteArray must beEqualTo("[source] foo\n[source] foo\n".getBytes())
    }

    "use ANSI for the prefix" in new Base {
      logger.sublogger("source", Some(Console.RED.getBytes())).out.println("foo")
      outStream.toByteArray must beEqualTo("\033[31m[source] \033[0mfoo\n".getBytes())
    }

    "put errors in bold" in new Base {
      logger.sublogger("source", Some(Console.YELLOW.getBytes())).err.println("foo")
      errStream.toByteArray must beEqualTo("\033[31;1mERROR - \033[0m\033[33m[source] \033[0mfoo\n".getBytes())
    }

    "let you treat errors as info" in new Base {
      val sublogger = logger.treatingErrorsAsInfo
      sublogger.out.println("out")
      sublogger.err.println("err")
      new String(errStream.toByteArray) must beEqualTo("")
      new String(outStream.toByteArray) must beEqualTo("out\nerr\n")
    }
  }
}
