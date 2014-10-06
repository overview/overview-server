package controllers.backend

import com.redis.RedisClient

trait RedisBackend extends Backend {
  protected lazy val redis: RedisClient = {
    import com.typesafe.plugin.use
    import play.api.Play.current
    import plugins.RedisPlugin

    use[RedisPlugin].client
  }
}
