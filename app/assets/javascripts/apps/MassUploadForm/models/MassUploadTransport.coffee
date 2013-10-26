define [
  'jquery'
  'underscore'
  'util/net/upload'
], ($, _, Upload) ->
  (options) ->
    url = options.url
    csrfToken = options.csrfToken

    doListFiles: (progress, success, error) ->
      $.get(url)
        .progress (jQueryProgressEvent) ->
          progress(_.pick(jQueryProgressEvent, 'total', 'loaded'))
        .done (json) ->
          success(
            json.files.map (file) ->
              name: file.name
              loaded: file.uploadedSize
              total: file.size
              lastModifiedDate: new Date(file.lastModifiedDate)
          )
        .fail ->
          error()

    doUploadFile: (file, progressCallback, successCallback, errorCallback) ->
      upload = new Upload(file, "#{url}/", {csrfToken: csrfToken})
      upload.start()
      upload
        .progress (progressEvent) ->
          progressCallback?(_.pick(progressEvent, 'total', 'loaded'))
        .done(successCallback)
        .fail(errorCallback)
      -> upload.abort()

    doDeleteFile: ->

    onUploadConflictingFile: ->
