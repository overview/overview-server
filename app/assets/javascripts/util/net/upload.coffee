define [ 'jquery', 'md5', 'util/shims/file' ], ($, md5) ->
  extend = $.extend
  Deferred = $.Deferred
  ajax = $.ajax
  md5 = CryptoJS.MD5

  states = {
    WAITING: 1   # Doing nothing
    STARTING: 2  # Waiting for server response before uploading
    UPLOADING: 3
    DONE: 4
    FAILED: 5    # Restart isn't possible
  }

  generate_uuid = (filename, file) ->
    # UUID v3: xxxxxxxx-xxxx-3xxx-yxxx-xxxxxxxxxxxx
    # where x is any hexadecimal digit and y is one of 8, 9, A, or B
    hash = md5("#{filename}::#{file.lastModifiedDate.toString()}::#{file.size}").toString()
    parts = []
    parts.push(hash[0...8])
    parts.push(hash[8...12])
    parts.push('3' + hash[13...16])
    y = (parseInt(hash[16...18], 16) & 0x3f | 0x80).toString(16)
    parts.push(y + hash[18...20])
    parts.push(hash[20...32])
    parts.join('-')

  # Converts from 1 to "WAITING", etc.
  state_to_key = (state) ->
    for k, v of states
      return k if v == state
    throw "Unknown state: #{state}"

  # A Content-Range header looks like "0-1233/1234"
  #
  # The "end" byte is inclusive in HTTP/1.1, but it's exclusive everywhere else
  # we deal with strings in our code. This method adds one to the provided number.
  content_range_to_end = (s) ->
    if (m = /bytes (\d+)-(\d+)\/(\d+)/.exec(s))?
      parseInt(m[2], 10) + 1
    else
      0

  encodeRfc5987ValueChars = (s) ->
    # Transcribed from https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/encodeURIComponent
    #
    # I'm not sure this is better than writing one that omits
    # encodeURIComponent(), but at least I'm reasonably certain it follows
    # the spec...
    encodeURIComponent(s)
      .replace(/['()]/g, escape)
      .replace(/\*/g, '%2A')
      .replace(/%(?:7C|60|5E)/g, unescape)

  ## We only read CHUNK_SIZE of the file into memory at a time, meaning we only
  ## send CHUNK_SIZE bytes at a time.
  #CHUNK_SIZE=16777216 # 16MB

  # Timeout for requesting status from the server.
  STARTING_TIMEOUT=30000 # 30s

  # Timeout for uploading chunks to the server. (If we hit this timeout, we'll
  # request status and pick up where we left off.)
  UPLOADING_TIMEOUT=600000 # 10min

  # Uploads a file to directory on the server. Supports resuming.
  #
  # A caller might do this:
  #
  #     upload = new Upload(file, url_prefix)
  #     # url will be url_prefix + a UUID
  #     # UUID will be consistent for the same file
  #
  #     upload.progress((e) -> console.log(e))
  #     upload.fail((e) -> console.log(e))
  #     upload.done((e) -> console.log(e))
  #     upload.always((e) -> console.log(e))
  #
  #     upload.start()
  #     ...
  #     upload.stop()
  #     ...
  #     upload.start()
  #
  #     console.log(upload.uploaded_offset, upload.bytes_total)
  #
  #     upload.abort()
  #
  # progress() callbacks are called with an object that looks like this:
  #     { state: state, loaded: 0, total: 1024 }
  #
  # `state` is one of the following:
  #
  # * "inactive": either stop() was called or start() never was.
  # * "synchronizing": checking with the server before uploading.
  # * "uploading": uploading.
  # * "done": done() callbacks are just about to be called.
  # * "failed": fail() callbacks are just about to be called.
  #
  # If the user modifies the disk before this Upload object is done, we are
  # guaranteed (by the File API's "snapshot state") to receive a
  # NotFoundError, SecurityError or NotReadableError. Any will lead us to fail.
  #
  # So after an Upload fails, you probably can't start() it again. But if you
  # stop() an upload yourself, you may start() it again to resume.
  #
  # Do not create and start() two Uploads with the same File. One or both will
  # fail in an unpredictable manner.
  class Upload
    # Builds a new Upload, but doesn't start it.
    #
    # Parameters:
    #     file: an HTML5 File object
    #     url_prefix: String. For instance, `http://example.org/upload/` will
    #                 send files to `http://example.org/upload/__uuid__`.
    #     options: Key/value pairs including these:
    #       contentType: Content-Type header (passed to jQuery.ajax())
    #       xhr_factory: function that accepts an "upload-progress" callback of
    #                    signature function(bytes_loaded, bytes_total) and returns
    #                    an XMLHttpRequest. (Defaults to HTML5-only code.)
    constructor: (@file, url_prefix, options={}) ->
      @filename = @file.webkitRelativePath || @file.name
      @url = "#{url_prefix}#{generate_uuid(@filename, @file)}"
      @url += "?csrfToken=#{encodeURIComponent(options.csrfToken)}" if options.csrfToken?
      @state = states.WAITING
      # uploaded_offset is accurate when leaving STARTING and entering UPLOADING.
      # We send a computed `loaded` variable when notifying progress() callbacks
      # while UPLOADING.
      @uploaded_offset = 0

      @options = extend({
        xhr_factory: (callback) ->
          xhr = new XMLHttpRequest()
          xhr.upload?.addEventListener 'progress', (e) ->
            callback(e.loaded, e.total)
          xhr

      }, options)

      # Copy jQuery's Deferred interface
      @deferred = new Deferred()
      for f in [ 'always', 'done', 'fail', 'pipe', 'progress', 'promise', 'then' ]
        this[f] = @deferred[f].bind(@deferred)

      undefined

    _set_state: (new_state) ->
      return if new_state == @state

      # When switching from an old state to a new one, we call a method like
      # "_waiting_to_starting".
      old_key = state_to_key(@state)
      new_key = state_to_key(new_state)

      method = "_#{old_key}_to_#{new_key}".toLowerCase()

      # The method must exist.
      throw "There is no method #{method} so we can't change state" if !this[method]?

      # If it returns false, we don't transition
      return if this[method].apply(this) is false

      # Now we're in the new state
      @state = new_state

    start: () -> this._set_state(states.STARTING) if @state != states.UPLOADING
    stop: () -> this._set_state(states.WAITING)
    abort: () -> this._set_state(states.FAILED) if @state != states.DONE

    _cancel_jqxhr: (name) ->
      this[name].abort() if this[name].state() == 'pending'
      delete this[name]

    _cancel_timeout: (name) ->
      window.clearTimeout(this[name]) if this[name] # okay if the ID is invalid
      delete this[name]

    #### STARTING
    #
    # When we enter the STARTING state, we fire a HEAD request to our @url. The
    # server returns the amount we've uploaded (Content-Range), or it returns a
    # 404 error if the upload hasn't begun.
    #
    # After the server responds, we move to UPLOADING. On failure, we spin.
    #
    # We can only transition to STARTING from WAITING. If the user requests to
    # start() an UPLOADING upload, we ignore the request.
    _waiting_to_starting: () ->
      this._to_starting()

    _uploading_to_starting: () ->
      this._from_uploading()
      this._to_starting()

    _from_starting: () ->
      this._cancel_jqxhr('starting_jqxhr')

    _to_starting: () ->
      @deferred.notify({ state: 'synchronizing' })

      @starting_jqxhr = ajax({
        url: @url
        type: 'HEAD'
        cache: false
      })

      # Somebody else might change the state. If they do, they'll modify
      # @starting_jqxhr.
      #
      # If that happens, we should do nothing.
      jqxhr = @starting_jqxhr
      jqxhr.always =>
        return if jqxhr isnt @starting_jqxhr

        if jqxhr.state() == 'resolved' || jqxhr.status == 404
          # We got a response from the server, so we're ready to upload
          @uploaded_offset = content_range_to_end(jqxhr.getResponseHeader('Content-Range'))

          if jqxhr.state() == 'resolved' && @uploaded_offset >= @file.size
            this._set_state(states.DONE)
          else
            this._set_state(states.UPLOADING)
        else
          # Failed! Retry
          console?.log("Error during upload. Retrying. jqxhr: ", jqxhr)
          this._cancel_jqxhr('starting_jqxhr')
          this._waiting_to_starting()

    #### WAITING
    #
    # When we enter the WAITING state, we cancel all requests to the server.
    _starting_to_waiting: () -> this._from_starting()
    _uploading_to_waiting: () -> this._from_uploading()

    #### UPLOADING
    #
    # When we enter UPLOADING, we start a massive POST request with a chunk of
    # the file. When the POST finishes, we start another. When it times out, we
    # revert to STARTING to verify that the upload is progressing properly.
    #
    # We can only enter UPLOADING from STARTING.
    _starting_to_uploading: () ->
      this._from_starting()
      this._to_uploading()

    _to_uploading: () ->
      @deferred.notify({ state: 'uploading', loaded: @uploaded_offset, total: @file.size })

      blob = @file.slice(@uploaded_offset, @file.size)

      jqxhr = undefined

      create_xhr = () =>
        @options.xhr_factory (loaded, total) =>
          return if !jqxhr? or jqxhr isnt @uploading_jqxhr
          @deferred.notify({ state: 'uploading', loaded: @uploaded_offset + loaded, total: @uploaded_offset + total })

      headers = {}

      headers['Content-Disposition'] = if /[^ !#$&+\-\.^_`|~0-9a-zA-Z]/.test(@filename)
        # There's a non-"token", as defined in http://tools.ietf.org/html/rfc2616#section-2.2
        "attachment; filename*=UTF-8''#{encodeRfc5987ValueChars(@filename)}"
      else
        "attachment; filename=\"#{@filename}\""

      if @file.size > 0
        sendOffset = @file.size - 1
        headers['Content-Range'] = "bytes #{@uploaded_offset}-#{sendOffset}/#{@file.size}"

      @uploading_jqxhr = ajax({
        url: @url
        type: 'POST'
        processData: false
        data: blob
        timeout: UPLOADING_TIMEOUT
        xhr: create_xhr
        contentType: @options.contentType || 'application/octet-stream'
        headers: headers
      })

      # Somebody else might change the state. If they do, they'll modify
      # @uploading_jqxhr.
      jqxhr = @uploading_jqxhr
      jqxhr.always =>
        return if jqxhr isnt @uploading_jqxhr

        if jqxhr.state() == 'resolved'
          this._set_state(states.DONE)
        else if jqxhr.status == 400 # Bad Request
          this._set_state(states.FAILED)
        else
          # Upload failed. Wait five seconds and try again
          @uploading_timeout = window.setTimeout(
            => this._set_state(states.STARTING)
          , 5000)

      undefined

    _from_uploading: () ->
      this._cancel_jqxhr('uploading_jqxhr')
      this._cancel_timeout('uploading_timeout')

    #### DONE
    #
    # When we enter the DONE state, the upload is successful. We can enter from
    # STARTING or UPLOADING.

    _to_done: () ->
      # Send a 100%-progress event
      @deferred.notify({ state: 'done', loaded: @file.size, total: @file.size })
      @deferred.resolve()

    _starting_to_done: () ->
      this._from_starting()
      this._to_done()

    _uploading_to_done: () ->
      this._from_uploading()
      this._to_done()

    #### FAILED
    #
    # When we enter the FAILED state, the upload cannot be resumed. We can enter
    # from STARTING and UPLOADING.

    _to_failed: () ->
      @deferred.notify({ state: 'failed', loaded: @uploaded_offset + 1, total: @file.size })
      @deferred.reject()

    _starting_to_failed: () ->
      this._from_starting()
      this._to_failed()

    _uploading_to_failed: () ->
      this._from_uploading()
      this._to_failed()
