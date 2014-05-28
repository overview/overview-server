package org.overviewproject.runner

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class ConfSpec extends Specification with Mockito {
  // XXX Scallop is extremely unreliable with unit tests, because it deals with
  // lazy variables and global objects. Sometimes half the tests fail; other
  // times, everything works.
  //
  // When editing Conf.scala, comment this line and retry the test suite
  // whenever a test fails. When you're done editing, please uncomment this
  // line so that the test suite passes every time, regardless of Scallop's
  // mood and the phase of the moon.
  args(skipAll=true)

  trait Base extends Scope {
    val repository : DaemonInfoRepository = mock[DaemonInfoRepository]
    val arguments : Seq[String] = Seq()

    def mockDaemonInfo(i: Int) : DaemonInfo = {
      val ret = mock[DaemonInfo]
      ret.id returns s"id$i"
      ret
    }

    def conf = {
      object ret extends Conf(repository, arguments) {
        override protected def onError(e: Throwable) : Unit = {
          throw e
        }
        afterInit()
      }
      ret
    }
  }

  trait ThreeDaemons extends Base {
    val allDaemonInfos = Seq(1, 2, 3).map(mockDaemonInfo(_))
    repository.allDaemonInfos returns allDaemonInfos
    repository.validKeys returns Set("id1", "id2", "id3")
  }

  "Conf" should {
    "give all daemons by default" in new Base {
      val expect = Seq(mockDaemonInfo(1), mockDaemonInfo(2))
      repository.allDaemonInfos returns(expect)
      conf.daemonInfos must beEqualTo(expect)
    }

    "allow -D properties" in new ThreeDaemons {
      override val arguments = Seq("-Dfoo=bar", "-Dbar=baz")
      conf.properties must beEqualTo(Map("foo" -> "bar", "bar" -> "baz"))
    }

    "ignore a daemon" in new ThreeDaemons {
      override val arguments = Seq("--except-servers", "id2")
      repository.daemonInfosExcept(Set("id2")) returns Seq(allDaemonInfos(0), allDaemonInfos(2))
      conf.daemonInfos must beEqualTo(Seq(allDaemonInfos(0), allDaemonInfos(2)))
    }

    "run just one daemon" in new ThreeDaemons {
      override val arguments = Seq("--only-servers", "id2")
      repository.daemonInfosOnly(Set("id2")) returns Seq(allDaemonInfos(1))
      conf.daemonInfos must beEqualTo(Seq(allDaemonInfos(1)))
    }

    "not run just one daemon with short argument" in new ThreeDaemons {
      // https://www.pivotaltracker.com/story/show/63669988
      override val arguments = Seq("-o", "id2")
      conf.daemonInfos must throwA[Throwable]("Unknown option 'o'")
    }

    "ignore two daemons" in new ThreeDaemons {
      override val arguments = Seq("--except-servers", "id1,id2")
      repository.daemonInfosExcept(Set("id1", "id2")) returns Seq(allDaemonInfos(2))
      conf.daemonInfos must beEqualTo(Seq(allDaemonInfos(2)))
    }

    "run just two daemons" in new ThreeDaemons {
      override val arguments = Seq("--only-servers", "id1,id2")
      repository.daemonInfosOnly(Set("id1", "id2")) returns Seq(allDaemonInfos(0), allDaemonInfos(1))
      conf.daemonInfos must beEqualTo(Seq(allDaemonInfos(0), allDaemonInfos(1)))
    }

    "point out invalid --only-servers values" in new ThreeDaemons {
      override val arguments = Seq("--only-servers", "id4")
      conf.daemonInfos must throwA[Throwable]("Bad arguments for option 'only-servers': 'id4' - Please specify a comma-separated list of valid servers, among these: id1,id2,id3")
    }

    "point out invalid --except-servers values" in new ThreeDaemons {
      override val arguments = Seq("--except-servers", "id4")
      conf.daemonInfos must throwA[Throwable]("Bad arguments for option 'except-servers': 'id4' - Please specify a comma-separated list of valid servers, among these: id1,id2,id3")
    }

    "error if both --only-servers and --except-servers are requested" in new ThreeDaemons {
      override val arguments = Seq("--only-servers", "id1", "--except-servers", "id2")
      conf.daemonInfos must throwA[Throwable]("There should be only one or zero of the following options: only-servers, except-servers")
    }

    "enable database by default" in new ThreeDaemons {
      conf.withDatabase() must beEqualTo(true)
    }

    "disable database" in new ThreeDaemons {
      override val arguments = Seq("--no-database")
      conf.withDatabase() must beEqualTo(false)
    }

    "add arbitrary sbt commands" in new ThreeDaemons {
      override val arguments = Seq("--sbt", "all/test")
      conf.sbtTask() must beEqualTo("all/test")
    }

    "add arbitrary sh commands" in new ThreeDaemons {
      override val arguments = Seq("--sh", "run something")
      conf.shTask() must beEqualTo("run something")
    }

    "not use a short option for sbt commands" in new ThreeDaemons {
      override val arguments = Seq("--s", "all/test")
      conf.daemonInfos must throwA[Throwable]("Unknown option 's'")
    }

    "include spaces in sbt commands" in new ThreeDaemons {
      override val arguments = Seq("--only-servers", "id1", "--sbt", "; project runner; test")
      repository.daemonInfosOnly(Set("id1")) returns Seq(allDaemonInfos(0))
      conf.sbtTask() must beEqualTo("; project runner; test")
    }
  }
}
