package org.overviewproject.runner

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class ConfSpec extends Specification with Mockito {
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
  }
}
