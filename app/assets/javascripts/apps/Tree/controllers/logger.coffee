define [ '../models/log' ], (Log) ->
  INTERVAL = 30000 # ms between POSTs

  class Logger
    constructor: () ->
      @log = new Log()
      @timeout = undefined
      @for_component = @log.for_component.bind(@log)

    _tick: () ->
      post = @log.upload_entries_to_server_and_clear()
      post.always =>
        # Regardless of success, keep going
        @timeout = window.setTimeout((=> @_tick()), INTERVAL)

    set_server: (@server) ->
      # TODO remove @server
      @timeout ||= window.setTimeout((=> @_tick()), INTERVAL)

  new Logger() # We return a singleton object, not a class!
