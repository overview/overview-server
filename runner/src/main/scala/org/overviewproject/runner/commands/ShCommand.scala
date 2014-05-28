package org.overviewproject.runner.commands

/** Constructs a Command to be run on a shell. */
class ShCommand(override val env: Seq[(String,String)], override val argv: Seq[String]) extends Command
