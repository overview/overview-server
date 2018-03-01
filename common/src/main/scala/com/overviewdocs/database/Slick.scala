package com.overviewdocs.database

import com.github.tminglei.slickpg._
import java.nio.charset.Charset
import java.time.Instant
import play.api.libs.json.{ JsObject, Json }

import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models.{DocumentDisplayMethod,DocumentSetUser,File2,UserRole}

trait MyPostgresProfile extends ExPostgresProfile
  with PgArraySupport
  with PgNetSupport
  with PgEnumSupport
{
  override val api = APIPlus

  object APIPlus extends API
    with ArrayImplicits
    with NetImplicits
    with SimpleArrayPlainImplicits {

    implicit val jsonTextColumnType = MappedColumnType.base[JsObject, String](
      Json.stringify,
      Json.parse(_).as[JsObject])

    implicit val jsonTextOptionColumnType = jsonTextColumnType.optionType

    implicit val intArrayColumnType = MappedColumnType.base[Array[Int], List[Int]](_.toList, _.toArray)
    implicit val intVectorColumnType = new SimpleArrayJdbcType[Int]("int4").to(_.toVector)

    implicit val instantColumnType = MappedColumnType.base[Instant, java.sql.Timestamp](
      { i => new java.sql.Timestamp(i.toEpochMilli) },
      { t => Instant.ofEpochMilli(t.getTime) }
    )

    implicit val instantOptionColumnType = instantColumnType.optionType

    implicit val userRoleColumnType = MappedColumnType.base[UserRole.Value, Int](_.id, UserRole(_))

    implicit val documentSetUserRoleColumnType = MappedColumnType.base[DocumentSetUser.Role, Int](
      _.isOwner match { case true => 1; case false => 2 },
      DocumentSetUser.Role.apply
    )

    implicit val file2MetadataColumnType = MappedColumnType.base[File2.Metadata, Array[Byte]](
      m => Json.toBytes(m.jsObject),
      v => File2.Metadata(Json.parse(v).as[JsObject])
    )

    implicit val file2PipelineOptionsFormat = Json.format[File2.PipelineOptions]
    implicit val file2PipelineOptionsColumnType = MappedColumnType.base[File2.PipelineOptions, Array[Byte]](
      v => Json.toBytes(Json.toJsObject(v)),
      b => Json.parse(b).asOpt[File2.PipelineOptions].getOrElse(File2.PipelineOptions(false, false, Vector()))
    )

    implicit val metadataSchemaTypeMapper = MappedColumnType.base[MetadataSchema, String](
      ms => ms.toJson.toString,
      s => MetadataSchema.fromJson(Json.parse(s))
    )

    implicit val documentDisplayMethodTypeMapper =
      createEnumJdbcType("document_display_method", DocumentDisplayMethod)

    implicit val documentDisplayMethodListTypeMapper =
      createEnumListJdbcType("document_display_method", DocumentDisplayMethod)

    implicit val documentDisplayMethodColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder(DocumentDisplayMethod)

    implicit val documentDisplayMethodOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder(DocumentDisplayMethod)
  }
}

/**
 * Our database driver.
 *
 * Usage:
 *
 *   import com.overviewdocs.database.Slick.api._
 *   ... do stuff like at http://slick.typesafe.com/doc/3.0.0
 */
object Slick extends MyPostgresProfile
