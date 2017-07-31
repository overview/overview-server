import com.typesafe.sbt.web.Import.WebKeys
import com.typesafe.sbt.packager.Keys._
import java.net.InetSocketAddress
import play.sbt.PlayImport.PlayKeys
import play.sbt.PlayRunHook
import sbt._
import scala.sys.process.{Process,ProcessLogger}

object PlayNpm extends AutoPlugin {
  object autoImport {
    lazy val npmPath = SettingKey[String]("npm-path", "The path to the `npm` command")
  }
  import autoImport._

  lazy val npmDistTask = Def.sequential(runNpmInstallTask, runNpmTask("dist"))

  private def runNpmInstallTask = Def.task {
    run(List(npmPath.value, "install"), Keys.baseDirectory.value, Keys.streams.value.log)
  }

  /** Runs a command from package.json's "scripts" Object */
  private def runNpmTask(command: String) = Def.task {
    run(List(npmPath.value, "run-script", command), Keys.baseDirectory.value, Keys.streams.value.log)
  }

  /** Runs a command */
  private def run(command: List[String], baseDirectory: File, logger: Logger): Unit = {
    start(command, baseDirectory, logger).exitValue match {
      case 0 => {}
      case code => throw new Exception(s"Command ${command.mkString(" ")} exited with code ${code}")
    }
  }

  /** Starts a Process */
  private def start(command: List[String], baseDirectory: File, logger: Logger): Process = {
    val builder = Process(command, baseDirectory)
    val processLogger = ProcessLogger((s => logger.info(s)), (s => logger.error(s)))
    builder.run(processLogger)
  }

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    npmPath := "npm",

    dist := dist.dependsOn(npmDistTask).value,
    stage := stage.dependsOn(npmDistTask).value,
    WebKeys.stage := WebKeys.stage.dependsOn(npmDistTask).value,
    Keys.cleanFiles += (Keys.baseDirectory { base => base / "public" / "javascript-bundles" }).value,
    Keys.cleanFiles += (Keys.baseDirectory { base => base / "node_modules" }).value,

    PlayKeys.playRunHooks += PlayNpmDev(npmPath.value, Keys.baseDirectory.value, Keys.streams.value.log)
  )

  case class PlayNpmDev(npmPath: String, baseDirectory: File, logger: Logger) extends PlayRunHook {
    var watchProcess: Option[Process] = None

    override def beforeStarted: Unit = {
      run(List(npmPath, "install"), baseDirectory, logger)
    }

    override def afterStarted(addr: InetSocketAddress): Unit = {
      watchProcess = Some(start(List(npmPath, "run-script", "dev"), baseDirectory, logger))
    }

    override def afterStopped: Unit = {
      watchProcess.map(_.destroy)
      watchProcess = None
    }
  }
}
