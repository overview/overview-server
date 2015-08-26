package controllers.backend

import com.redis.RedisClient
import play.api.Play

import modules.RedisModule

trait RedisBackend extends Backend {
  protected lazy val redis: RedisClient = Play.current.injector.instanceOf[RedisModule].client
}
