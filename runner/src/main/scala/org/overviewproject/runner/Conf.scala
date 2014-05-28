package org.overviewproject.runner

import java.io.File
import org.rogach.scallop.{ArgType,ScallopConf,ValueConverter}
import scala.reflect.runtime.universe.typeTag

/** Command-line argument parser.
  *
  * Usage:
  *
  *   val conf = new Conf(daemonInfoRepository, arguments)
  *   val daemonInfos = conf.daemonInfos
  */
class Conf(daemonInfoRepository: DaemonInfoRepository, arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Overview Development Version Runner")
  banner(s"""Usage: run [OPTION]
            |By default, runs all servers. You may specify --only-servers or
            |--except-servers if you want to avoid some.
            |
            |Servers: ${daemonInfoRepository.allDaemonInfos.map(_.id).mkString(",")}
            |
            |Options:
            |""".stripMargin)

  def daemonInfoListConverter = new ValueConverter[Seq[DaemonInfo]] {
    override def parse(s: List[(String, List[String])]) : Either[String, Option[Seq[DaemonInfo]]] = {
      if (s.isEmpty) {
        Right(None)
      } else {
        val isOnly = s(0)._1 == "only-servers"
        val keys = s.map(_._2).flatten.mkString(",").split(',').toSet
        val invalidKeys = keys -- daemonInfoRepository.validKeys

        if (invalidKeys.isEmpty) {
          val specs = if (isOnly) daemonInfoRepository.daemonInfosOnly(keys) else daemonInfoRepository.daemonInfosExcept(keys)
          Right(Some(specs))
        } else {
          Left(s"Please specify a comma-separated list of valid servers, among these: ${daemonInfoRepository.validKeys.mkString(",")}")
        }
      }
    }

    override val tag = typeTag[Seq[DaemonInfo]]
    override val argType = ArgType.SINGLE
  }

  val onlyServers = opt[Seq[DaemonInfo]]("only-servers", descr="Only start this comma-separated list of servers", noshort=true)(daemonInfoListConverter)
  val exceptServers = opt[Seq[DaemonInfo]]("except-servers", descr="Start all but this comma-separated list of servers", noshort=true)(daemonInfoListConverter)
  val withDatabase = toggle("database", descrYes="Run Postgres (and evolutions) too", default=Some(true), prefix="no-", noshort=true)
  val sbtTask = opt[String]("sbt", descr="Also run this sbt task", noshort=true, default=None)
  val shTask = opt[String]("sh", descr="Also run this command on the shell", noshort=true, default=None)
  /** "System properties" a la JVM. We do _not_ add these to the running JVM. */
  val properties = props[String]()

  mutuallyExclusive(onlyServers, exceptServers)

  def databasePath : String = {
    new File("database").getAbsolutePath()
  }

  def daemonInfos : Seq[DaemonInfo] = {
    onlyServers.get
      .orElse(exceptServers.get)
      .getOrElse(daemonInfoRepository.allDaemonInfos)
  }
}
