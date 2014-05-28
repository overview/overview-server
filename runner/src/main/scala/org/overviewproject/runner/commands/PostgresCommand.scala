package org.overviewproject.runner.commands

import java.io.File

class PostgresCommand(override val argv: Seq[String]) extends Command {
  override val env = Seq()

  def withSubArgs(subArgs: Seq[String]) : PostgresCommand = new PostgresCommand(argv ++ subArgs)
}

object PostgresCommand {
  val UnixStandardSearchPaths : Iterable[String] = Seq(
    // Standard systems
    "/usr/sbin",
    "/usr/local/sbin",
    // Ubuntu
    "/usr/lib/postgresql/9.3/bin",
    "/usr/lib/postgresql/9.2/bin",
    "/usr/lib/postgresql/9.1/bin",
    "/usr/lib/postgresql/9.0/bin",
    // Fedora, according to http://koji.fedoraproject.org/koji/rpminfo?rpmID=4676802
    // Mac OS X Server default, starting 10.7, according to http://stackoverflow.com/questions/6770649/repairing-postgresql-after-upgrading-to-osx-10-7-lion
    "/usr/bin",
    // Homebrew, according to http://stackoverflow.com/questions/6770649/repairing-postgresql-after-upgrading-to-osx-10-7-lion
    "/usr/local/bin",
    // Postgres default
    "/usr/local/pgsql/bin",
    // Postgres suggestion on http://www.postgresql.org/docs/9.2/static/install-procedure.html
    "/opt/local/lib",
    // MacPorts, according to https://trac.macports.org/browser/trunk/dports/databases/postgresql91/Portfile
    "/opt/local/lib/postgresql93/bin",
    "/opt/local/lib/postgresql92/bin",
    "/opt/local/lib/postgresql91/bin",
    "/opt/local/lib/postgresql90/bin",
    // Postgres.app, as per http://postgresapp.com/documentation
    "/Applications/Postgres.app/Contents/MacOS/bin",
    "/Applications/Postgres93.app/Contents/MacOS/bin",
    // Postgres.app, as of 2014-04-25, has a different bin path
    "/Applications/Postgres.app/Contents/Versions/9.3/bin",
    // Fink, according to http://pdb.finkproject.org/pdb/package.php/postgresql92
    "/sw/opt/postgresql-9.3/bin",
    "/sw/opt/postgresql-9.2/bin",
    "/sw/opt/postgresql-9.1/bin",
    "/sw/opt/postgresql-9.0/bin",
    // EnterpriseDB, according to http://www.enterprisedb.com/resources-community/pginst-guide
    "/opt/PostgreSQL/9.3/bin",
    "/opt/PostgreSQL/9.2/bin",
    "/opt/PostgreSQL/9.1/bin",
    "/opt/PostgreSQL/9.0/bin",
    "/Library/PostgreSQL/9.3/bin",
    "/Library/PostgreSQL/9.2/bin",
    "/Library/PostgreSQL/9.1/bin",
    "/Library/PostgreSQL/9.0/bin"
  )

  val RequiredCommands : Seq[String] = Seq("initdb", "postgres")
  val RequiredExeCommands : Seq[String] = RequiredCommands.map(_ + ".exe")

  trait Filesystem {
    def isFileExecutable(path: String) : Boolean
    def programFilesPaths: Seq[String]
    def envPaths: Seq[String]
  }

  object Filesystem extends Filesystem {
    override def isFileExecutable(path: String) : Boolean = new File(path).canExecute
    override def programFilesPaths : Seq[String] = Seq() ++ sys.env.get("ProgramFiles") ++ sys.env.get("ProgramW6432") ++ sys.env.get("ProgramFiles(x86)")
    override def envPaths: Seq[String] = {
      sys.env.getOrElse("PATH", "")
        .split(File.pathSeparator)
        .filter(_.length > 0)
    }
  }

  def windowsSearchPaths(filesystem: Filesystem) : Iterable[String] = {
    filesystem.programFilesPaths.flatMap({ (s: String) => Seq(
      // EnterpriseDB, according to http://www.enterprisedb.com/resources-community/pginst-guide
      s"$s\\PostgreSQL\\9.3\\bin",
      s"$s\\PostgreSQL\\9.2\\bin",
      s"$s\\PostgreSQL\\9.1\\bin",
      s"$s\\PostgreSQL\\9.0\\bin"
    )})
  }

  private def findAbsoluteSbinPath(commands: Seq[String], filesystem: Filesystem) : Either[String,String] = {
    def isPostgresHere(path: String) : Boolean = {
      commands.forall(c => filesystem.isFileExecutable(new File(path, c).toString))
    }
    val allPaths = filesystem.envPaths ++ windowsSearchPaths(filesystem) ++ UnixStandardSearchPaths
    allPaths
      .find(isPostgresHere(_))
      .toRight(s"Could not find Postgres 9.0-9.3. Please install ${commands.mkString(", ")} (which must be executable) in one of: ${allPaths.mkString(", ")}")
  }

  def apply(basename: String, args: String*) : PostgresCommand = {
    apply(Filesystem, basename, args: _*)
  }

  def apply(filesystem: Filesystem, basename: String, args: String*) : PostgresCommand = {
    val maybeFile : Either[String,File] = findAbsoluteSbinPath(RequiredCommands, filesystem).fold(
      err => findAbsoluteSbinPath(RequiredExeCommands, filesystem).fold(
        err2 => Left(err),
        path => Right(new File(path, basename + ".exe"))
      ),
      path => Right(new File(path, basename))
    )

    maybeFile.fold(
      err => throw new Exception(err),
      file => new PostgresCommand(Seq(file.toString) ++ args)
    )
  }
}
