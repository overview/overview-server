package views.json.admin.Job

import play.api.i18n.Messages
import play.api.libs.json.{JsArray,JsString,JsValue,Json}

import com.overviewdocs.models.{CsvImportJob,DocumentSet,DocumentSetCreationJobImportJob,FileGroupImportJob,ImportJob,Tree}

object index {
  def apply(
    jobs: Seq[(ImportJob,DocumentSet,Option[String])],
    trees: Seq[(Tree,DocumentSet,Option[String])]
  )(implicit messages: Messages): JsValue = {
    Json.obj(
      "importJobs" -> indexJobs(jobs),
      "trees" -> indexTrees(trees)
    )
  }

  private def indexJobs(jobs: Seq[(ImportJob,DocumentSet,Option[String])])(implicit messages: Messages): JsValue = {
    val jsons: Seq[JsValue] = jobs.map(job => Json.obj(
      "id" -> JsString(job._1 match {
        // Client needs stable IDs
        case CsvImportJob(ci) => s"csv-import-${ci.id}"
        case DocumentSetCreationJobImportJob(j) => s"dscj-${j.id}"
        case FileGroupImportJob(fg) => s"file-group-${job._2.id}-${fg.id}"
      }),
      "documentSetId" -> job._2.id,
      "documentSetTitle" -> job._2.title,
      "progress" -> job._1.progress,
      "progressDescription" -> job._1.description.map(t => Messages(t._1, t._2: _*)),
      "ownerEmail" -> job._3,
      "deleteUrl" -> JsString(job._1 match {
        case CsvImportJob(ci) => controllers.admin.routes.JobController.destroyCsvImport(ci.documentSetId, ci.id).toString
        case DocumentSetCreationJobImportJob(j) => controllers.admin.routes.JobController.destroyDscj(j.id).toString
        case FileGroupImportJob(fg) => controllers.admin.routes.JobController.destroyFileGroup(job._2.id, fg.id).toString
      })
    ))
    JsArray(jsons)
  }

  private def indexTrees(trees: Seq[(Tree,DocumentSet,Option[String])])(implicit messages: Messages): JsValue = {
    val jsons: Seq[JsValue] = trees.map(tree => Json.obj(
      "id" -> s"tree-${tree._1.id}", // Client needs stable IDs
      "documentSetId" -> tree._2.id,
      "documentSetTitle" -> tree._2.title,
      "progress" -> tree._1.progress,
      "progressDescription" -> Messages(s"views.Tree.progressDescription.${tree._1.progressDescription}"),
      "ownerEmail" -> tree._3,
      "deleteUrl" -> controllers.admin.routes.JobController.destroyTree(tree._1.id).toString
    ))
    JsArray(jsons)
  }
}
