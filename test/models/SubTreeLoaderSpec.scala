package models

import anorm._ 
import anorm.SqlParser._
import java.sql.Connection

import scala.collection.JavaConversions._

import org.specs2.mutable.Specification
import play.api.db.DB
import play.api.Play.current

import helpers.DbTestContext

class SubTreeLoaderSpec extends Specification {

  "SubTreeLoader" should {
    
    
    def writeBinaryTreeInDb(depth : Int) : List[Long] = {
      DB.withConnection { implicit connection => 
        val id = SQL(
              """
                insert into node values (nextval('node_seq'), {description});
              """
            ).on("description" -> "root").executeInsert().getOrElse(-1l)
            
        id :: writeSubTreeInDb(id, depth - 1) 
      }
    }

    
    def writeSubTreeInDb(root : Long, depth: Int)(implicit connection : Connection) : List[Long] = depth match {
      case 0 => Nil
      case _ => {
        
        val childId1 = SQL("insert into Node values (nextval('node_seq'), {description}, {parent})").
          on("description" -> ("heightA-" + depth), "parent" -> root).
          executeInsert().getOrElse(-1l)
        val childId2 = SQL("insert into Node values (nextval('node_seq'), {description}, {parent})").
          on("description" -> ("heightB-" + depth), "parent" -> root).
          executeInsert().getOrElse(-1l)
      
        childId1 :: childId2 ::
          writeSubTreeInDb(childId1, depth - 1) ++ writeSubTreeInDb(childId2, depth - 1)
      }
    }
    
    trait TreeContext extends DbTestContext {
      lazy val nodeIds = writeBinaryTreeInDb(10)
    }
    
    "load subtree 5 levels deep" in new TreeContext {

      val subTreeLoader = new SubTreeLoader(nodeIds(0))
      
      val nodes = subTreeLoader.loadNodes()
      
      nodes must have size(31)
    }
  }
}