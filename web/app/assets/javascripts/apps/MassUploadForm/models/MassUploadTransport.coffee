define [
  'jquery'
  'underscore'
  'util/BlobHasher'
  'util/net/upload'
], ($, _, BlobHasher, NetUpload) ->
  class MassUploadTransport
    constructor: (options) ->
      throw 'Must set options.url, a String' if !options.url
      throw 'Must set options.csrfToken, a String' if !options.csrfToken

      @url = options.url
      @csrfToken = options.csrfToken
      @uniqueCheckUrlPrefix = options.uniqueCheckUrlPrefix
      @blobHasher = new BlobHasher

      _.bindAll(@, 'doListFiles', 'doUploadFile', 'doDeleteFile', 'onUploadConflictingFile')

    # Calls callback(null, true) when the server already has this sha1
    # Calls callback(null, false) when the server does not
    # Calls callback(err) when there is an HTTP error (other than a 404)
    sha1ForBlobExists: (blob, callback) ->
      return callback(null, false) if !@uniqueCheckUrlPrefix

      @blobHasher.sha1 blob, (err, hashBuffer) =>
        return callback(err) if err?

        hashArray = new Uint8Array(hashBuffer)
        h = (b) -> (0x100 | b).toString(16).substring(1)
        sha1 = (h(i) for i in hashArray).join('')

        $.ajax
          type: 'HEAD'
          url: "#{@uniqueCheckUrlPrefix}/#{sha1}"
          success: -> callback(null, true)
          error: (jqxhr, textStatus, errorThrown) ->
            if jqxhr.status == 404
              callback(null, false)
            else
              console.warn("HTTP uniqueness query failed. #{textStatus}: #{errorThrown}")
              callback(new Error("HTTP uniqueness query failed."))

    doListFiles: (progress, done) ->
      $.get(@url)
        .progress (jQueryProgressEvent) ->
          progress(_.pick(jQueryProgressEvent, 'total', 'loaded'))
        .done (json) ->
          done(null, json.files.map((file) ->
              name: file.name
              loaded: file.uploadedSize
              total: file.size
              lastModifiedDate: new Date(file.lastModifiedDate)
          ))
        .fail (jqxhr, textStatus, errorThrown) ->
          error = new Error("AJAX error. #{textStatus}: #{errorThrown}")
          error.jqxhr = jqxhr
          done(error)

    doUploadFile: (upload, progress, done) ->
      aborted = false
      file = upload.get('file')
      throw new Error('Must pass an Upload with a File') if !file?
      netUpload = null

      abort = ->
        aborted = true
        netUpload?.abort()

      @sha1ForBlobExists file, (err, exists) =>
        if aborted
          done(null)
        else if err?
          done(err)
        else if exists
          upload.set(skippedBecauseAlreadyInDocumentSet: true)
          done(null)
        else
          netUpload = new NetUpload(file, "#{@url}/", {csrfToken: @csrfToken})
          netUpload
            .progress((e) -> progress(total: e.total, loaded: e.loaded))
            .done(-> done(null))
            .fail((error) -> done(error))
          netUpload.start()

      abort

    doDeleteFile: ->

    onUploadConflictingFile: ->

  (options) -> new MassUploadTransport(options)
