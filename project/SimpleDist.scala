package sbt

import Keys._

trait OverviewKeys {
  val distDirectory = SettingKey[File]("dist2")
}

object OverviewKeys extends OverviewKeys

import OverviewKeys._

trait OverviewCommands  {

  val packageAllProjects = TaskKey[Seq[File]]("package-other-projects")
  val packageAllProjectsTask = (thisProjectRef, state).flatMap { (project, state) =>

    val structure: Load.BuildStructure = Project structure state
    val dependencies = Project.getProject(project, structure).toList.flatMap { p => p.dependencies.map(_.project) }
    val allProjects = project +: dependencies

    val packageProjectTasks: Seq[Task[Map[Artifact, File]]] = allProjects.flatMap { p =>
      packagedArtifacts.task in p get structure.data
    }

    def artifactMapToJarFiles(m: Map[Artifact,File]) = m.filterKeys(_.extension == "jar").values

    val jars: Task[Seq[File]] = packageProjectTasks.join.map(_.flatMap(artifactMapToJarFiles).distinct)

    jars
  }

  
  val simpleDist = TaskKey[Unit]("simple-dist", "Package up project")
  val simpleDistTask = (distDirectory, packageAllProjects, dependencyClasspath in Runtime, normalizedName, version) map { (dist, projectPackages, dependencies, id, version) =>

    val packageName = id + "-" + version
    val zip = dist / (packageName + ".zip")

    IO.delete(dist)
    IO.createDirectory(dist)

    val libs = {
      dependencies.filter(_.data.ext == "jar").map { dependency =>
        val filename = dependencyFilenameInDist(dependency)
        val path = ("lib/" + filename.getOrElse(dependency.data.getName))
        dependency.data -> path
      } ++ projectPackages.map(jar => jar -> ("lib/" + jar.getName))
    }

    IO.zip(libs.map { case (jar, path) => jar -> (packageName + "/" + path) }, zip)
  }

  private def dependencyFilenameInDist(dependency: Attributed[File]): Option[String] =
   for {
     module <- dependency.metadata.get(AttributeKey[ModuleID]("module-id"))
     artifact <- dependency.metadata.get(AttributeKey[Artifact]("artifact"))
   } yield {
     module.organization + "." + module.name + "-" +
     Option(artifact.name.replace(module.name, "")).filterNot(_.isEmpty).map(_ + "-").getOrElse("") +
     module.revision + ".jar"
   }
  
  lazy val defaultSettings = Seq[Setting[_]](
    distDirectory <<= baseDirectory / "dist",
    simpleDist <<= simpleDistTask,
    packageAllProjects <<= packageAllProjectsTask,
    cleanFiles <+= distDirectory,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false
  )
}

object OverviewCommands extends OverviewCommands
