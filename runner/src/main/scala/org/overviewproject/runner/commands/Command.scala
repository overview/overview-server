package org.overviewproject.runner.commands

import scala.sys.process.{ProcessBuilder,Process}

/** A command that can be run.
  *
  * This can encompass any command. The only requirement: it must be run in
  * a separate process -- i.e., it can be run from a shell.
  */
class Command(val env: Seq[(String,String)], val argv: Seq[String]) {
  /** Describes this command as it might look in a shell.
    *
    * The intent is that a user could copy/paste the command into a shell and
    * the shell would run it the same way. Obviously, that depends on which
    * shell the user is using. Glitches are allowed.
    */
  override def toString: String = (env.map { case (k: String, v: String) => s"$k='$v'" } ++ argv.map(x => s"'$x'")).mkString(" ")

  /** The ProcessBuilder to run.
    */
  val processBuilder : ProcessBuilder = Process(argv, None, env: _*)
}
