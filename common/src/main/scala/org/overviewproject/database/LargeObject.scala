package org.overviewproject.database

import org.postgresql.PGConnection
import org.postgresql.largeobject.{LargeObject=>PGLargeObject}
import slick.dbio.{DBIOAction,Effect,NoStream,SynchronousDatabaseAction}
import slick.jdbc.JdbcBackend
import slick.util.DumpInfo

/** Provides access to a single LargeObject.
  *
  * The methods here return `DBIOAction` instances. That means they're meant to
  * be strung together and executed using `database.run()`. See
  * `LargeObjectManager` for intended usage pattern.
  *
  * The LargeObject itself is returned from a DBIOAction; that action is the
  * one that creates the org.postgresql.largeobject.LargeObject.
  */
class LargeObject(pgLargeObject: PGLargeObject) {
  /** Returns the OID of the object.
    */
  def oid: Long = pgLargeObject.getLongOID

  /** Seeks the cursor to the given position.
    *
    * This internally uses seek64(), to support large files.
    */
  def seek(position: Long, reference: LargeObject.Seek = LargeObject.Seek.Set)
  : DBIOAction[Unit, NoStream, Effect.Transactional] = {
    new LargeObject.SeekAction(pgLargeObject, position, reference)
  }

  /** Reads up to length bytes from the large object.
    *
    * No need to pass a buffer in: the JDBC Postgres driver creates a new Array
    * internally, so this is actually the lowest-level method available.
    */
  def read(length: Int)
  : DBIOAction[Array[Byte], NoStream, Effect.Read with Effect.Transactional] = {
    new LargeObject.ReadAction(pgLargeObject, length)
  }

  /** Reads from the array and writes to the LargeObject.
    *
    * Returns the number of bytes written.
    */
  def write(buffer: Array[Byte], offset: Int, length: Int)
  : DBIOAction[Unit, NoStream, Effect.Write with Effect.Transactional] = {
    new LargeObject.WriteAction(pgLargeObject, buffer, offset, length)
  }

  /** Shortcut for writing from the entire array.
    */
  def write(buffer: Array[Byte])
  : DBIOAction[Unit, NoStream, Effect.Write with Effect.Transactional] = {
    write(buffer, 0, buffer.length)
  }
}

object LargeObject {
  sealed trait Seek
  object Seek {
    case object Current extends Seek
    case object End extends Seek
    case object Set extends Seek
  }

  sealed trait Mode
  object Mode {
    case object Read extends Mode
    case object Write extends Mode
    case object ReadWrite extends Mode
  }

  private abstract class LargeObjectAction[R, -E <: Effect]
    extends SynchronousDatabaseAction[R, NoStream, JdbcBackend, E]
  {
    def runInternal: R // shortcut, no arg required
    override def run(context: JdbcBackend#Context): R = runInternal
  }

  private class ReadAction(pgLargeObject: PGLargeObject, length: Int)
    extends LargeObjectAction[Array[Byte], Effect.Read with Effect.Transactional]
  {
    override def getDumpInfo = DumpInfo("LargeObject.ReadAction", s"${pgLargeObject.getLongOID},$length")
    override def runInternal: Array[Byte] = pgLargeObject.read(length)
  }

  private class SeekAction(pgLargeObject: PGLargeObject, position: Long, reference: LargeObject.Seek)
    extends LargeObjectAction[Unit, Effect.Transactional]
  {
    override def getDumpInfo = DumpInfo("LargeObject.SeekAction", s"${pgLargeObject.getLongOID},$position")
    private def pgReference: Int = reference match {
      case Seek.Current => PGLargeObject.SEEK_CUR
      case Seek.End => PGLargeObject.SEEK_END
      case Seek.Set => PGLargeObject.SEEK_SET
    }

    override def runInternal: Unit = pgLargeObject.seek64(position, pgReference)
  }

  private class WriteAction(pgLargeObject: PGLargeObject, buffer: Array[Byte], offset: Int, length: Int)
    extends LargeObjectAction[Unit, Effect.Write with Effect.Transactional]
  {
    override def getDumpInfo = DumpInfo("LargeObject.WriteAction", s"${pgLargeObject.getLongOID},$offset,$length")
    override def runInternal: Unit = pgLargeObject.write(buffer, offset, length)
  }
}
