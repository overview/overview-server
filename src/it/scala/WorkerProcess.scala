package steps

/** Manages a worker process.
  *
  * Usage:
  *
  *   val wp = new WorkerProcess() // does nothing
  *   wp.start // starts sbt using same datasource.default.url; runs "worker/run"
  *   wp.stop // stops process, but leaves sbt running
  *   wp.start // runs "worker/run"
  *   wp.stop // stops process, leaves sbt running
  */
class WorkerProcess {
  var running : Boolean = false
  lazy private val sbt = launchSbt

  def launchSbt : Process = {
    import java.io.File.separator

    val ivyHome = sys.props("sbt.ivy.home")
    val javaHome = sys.props("java.home")
    val playHome = sys.props("play.home")
    val sbtBootProperties = sys.props("sbt.boot.properties")
    val databaseUrl = sys.props("datasource.default.url")

    val javaBin = javaHome + separator + "bin" + separator + "java"
    val sbtJar = playHome + separator + "sbt" + separator + "sbt-launch.jar"

    val processBuilder = new ProcessBuilder(
      javaBin,
      "-Dsbt.ivy.home=" + ivyHome,
      "-Dplay.home=" + playHome,
      "-Dsbt.boot.properties=" + sbtBootProperties,
      "-Ddatasource.default.url=" + databaseUrl,
      "-jar", sbtJar
    )

    processBuilder.start
  }

  def start = {
    if (!running) {
      running = true
      sbt.getOutputStream.write("worker/run\n".getBytes)
      sbt.getOutputStream.flush
    }
  }

  def stop = {
    if (running) {
      running = false
      val EOF = 4
      sbt.getOutputStream.write(EOF)
      sbt.getOutputStream.flush
    }
  }
}
