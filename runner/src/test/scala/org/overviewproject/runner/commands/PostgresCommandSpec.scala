package org.overviewproject.runner.commands

import java.io.File
import org.specs2.mutable.Specification

class PostgresCommandSpec extends Specification {
  case class MockFile(path: String, isExecutable: Boolean)
  class MockFilesystem(val files: Iterable[MockFile], val path: Seq[String] = Seq(), val envProgramFiles: Option[String] = None) extends PostgresCommand.Filesystem {
    def isFileExecutable(path: String) : Boolean = {
      files.find(_.path == path).map(_.isExecutable).getOrElse(false)
    }

    def programFilesPath = envProgramFiles

    def envPaths = path
  }

  "PostgresCommand" should {
    "find initdb/postgres in /usr/sbin" in {
      val fs = new MockFilesystem(Seq(
        MockFile("/usr/sbin/postgres", true),
        MockFile("/usr/sbin/initdb", true)
      ))

      PostgresCommand(fs, "initdb").argv(0) must beEqualTo("/usr/sbin/initdb")
      PostgresCommand(fs, "postgres").argv(0) must beEqualTo("/usr/sbin/postgres")
    }

    "not find initdb/postgres if one is not executable" in {
      val fs = new MockFilesystem(Seq(
        MockFile("/usr/sbin/postgres", false),
        MockFile("/usr/sbin/initdb", true)
      ))

      PostgresCommand(fs, "initdb") must throwA[Exception]
    }

    "not find initdb/postgres if one is not present" in {
      val fs = new MockFilesystem(Seq(
        MockFile("/usr/sbin/postgres", true)
      ))

      PostgresCommand(fs, "initdb") must throwA[Exception]
    }

    "find initdb/postgres in /usr/local/sbin" in {
      val fs = new MockFilesystem(Seq(
        MockFile("/usr/local/sbin/postgres", true),
        MockFile("/usr/local/sbin/initdb", true)
      ))

      PostgresCommand(fs, "initdb").argv(0) must beEqualTo("/usr/local/sbin/initdb")
    }

    "not find initdb/postgres if they are in different (but valid) paths" in {
      val fs = new MockFilesystem(Seq(
        MockFile("/usr/local/sbin/postgres", true),
        MockFile("/usr/sbin/initdb", true)
      ))

      PostgresCommand(fs, "initdb") must throwA[Exception]
    }

    "find initdb/postgres if they are in the $PATH" in {
      val fs = new MockFilesystem(Seq(
        MockFile("/weird/place/postgres", true),
        MockFile("/weird/place/initdb", true)
      ), Seq("/something", "/weird/place", "/somethingelse"))

      PostgresCommand(fs, "initdb").argv(0) must beEqualTo("/weird/place/initdb")
    }

    "find initdb/postgres if they are in %PROGRAM_FILES%" in {
      val fs = new MockFilesystem(Seq(
        MockFile("c:\\Program Files\\PostgreSQL\\9.3\\bin" + File.separator + "initdb", true),
        MockFile("c:\\Program Files\\PostgreSQL\\9.3\\bin" + File.separator + "postgres", true)
      ), Seq(), Some("c:\\Program Files"))

      PostgresCommand(fs, "initdb").argv(0) must beEqualTo("c:\\Program Files\\PostgreSQL\\9.3\\bin" + File.separator + "initdb")
    }

    "find initdb/postgres if they are in %PROGRAM_FILES% and end with .exe" in {
      val fs = new MockFilesystem(Seq(
        MockFile("c:\\Program Files\\PostgreSQL\\9.3\\bin" + File.separator + "initdb.exe", true),
        MockFile("c:\\Program Files\\PostgreSQL\\9.3\\bin" + File.separator + "postgres.exe", true)
      ), Seq(), Some("c:\\Program Files"))

      PostgresCommand(fs, "initdb").argv(0) must beEqualTo("c:\\Program Files\\PostgreSQL\\9.3\\bin" + File.separator + "initdb.exe")
    }
  }
}
