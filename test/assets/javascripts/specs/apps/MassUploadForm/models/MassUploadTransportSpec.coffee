define [
  'backbone'
  'apps/MassUploadForm/models/MassUploadTransport',
  'util/net/upload'
], (Backbone, MassUploadTransport, Upload) ->
  describe 'apps/MassUploadForm/models/MassUploadTransport', ->
    progressSpy = undefined
    successSpy = undefined
    errorSpy = undefined
    transport = undefined

    beforeEach ->
      progressSpy = jasmine.createSpy()
      successSpy = jasmine.createSpy()
      errorSpy = jasmine.createSpy()
      transport = MassUploadTransport( {url: '/files', csrfToken: 'a_token'} )

    describe 'doListFiles', ->
      it 'makes an ajax request for the /files endpoint', ->
        transport.doListFiles()
        expect(mostRecentAjaxRequest().url).toEqual('/files')
        expect(mostRecentAjaxRequest().method).toEqual('GET')

      it 'calls the progress callback when it gets data', ->
        deferred = new $.Deferred
        spyOn($, 'ajax').andReturn(deferred)
        transport.doListFiles(progressSpy)
        expect(progressSpy).not.toHaveBeenCalled()
        deferred.notify({ lengthComputable: true, total: 10, loaded: 5})
        expect(progressSpy).toHaveBeenCalledWith({total: 10, loaded: 5})

      it 'calls the success callback when the upload finishes', ->
        ajaxResponse =
          files: [
            guid: 'foo'
            name: 'some-file.pdf'
            lastModifiedDate: '2013-10-01T12:19:01Z'
            size: 10
            uploadedSize: 8
          ]

        transport.doListFiles((() ->), successSpy)
        mostRecentAjaxRequest().response
          status: 200
          responseText: JSON.stringify(ajaxResponse)

        expect(successSpy).toHaveBeenCalledWith( [
          name: 'some-file.pdf',
          loaded: 8
          total: 10
          lastModifiedDate: new Date('2013-10-01T12:19:01Z')
        ] )

      it 'calls the error callback', ->
        transport.doListFiles((() ->), (() ->), errorSpy)
        mostRecentAjaxRequest().response
          status: 404

        expect(errorSpy).toHaveBeenCalled()

    describe 'doUploadFile', ->
      file = undefined
      uploadSpy = undefined
      upload = undefined

      beforeEach ->
        file = {
          name: 'foo.pdf'
          lastModifiedDate: new Date('2013-10-01T12:00:00Z')
        }
        uploadSpy = spyOn(Upload.prototype, 'start').andCallThrough()

        transport.doUploadFile(file, progressSpy, successSpy, errorSpy)
        upload = uploadSpy.mostRecentCall.object

      it 'uploads the file', ->
        transport.doUploadFile(file)
        expect(uploadSpy).toHaveBeenCalled()
        upload = uploadSpy.mostRecentCall.object
        expect(upload.url).toMatch(/\/files\/\w+/)
        expect(upload.file).toBe(file)

      it 'calls the progress callback while uploading', ->
        upload.deferred.notify({ state: 'uploading', total: 10, loaded: 5})
        expect(progressSpy).toHaveBeenCalledWith({total: 10, loaded: 5})

      it 'calls the success callback while uploading', ->
        upload.deferred.resolve()
        expect(successSpy).toHaveBeenCalled()

      it 'calls the error callback on error', ->
        upload.deferred.reject()
        expect(errorSpy).toHaveBeenCalled()

      it 'includes the CSRF token in the url', ->
        transport.doUploadFile(file)
        expect(uploadSpy).toHaveBeenCalled()
        upload = uploadSpy.mostRecentCall.object
        expect(upload.url).toMatch(/[\?&]csrfToken=a_token/)


    describe 'doDeleteFile', ->
      it 'exists, but does not do anything for now', ->
        expect(transport.doDeleteFile).toEqual(jasmine.any(Function))

    describe 'onUploadConflicting', ->
      it 'exists, but does not do anything for now', ->
        expect(transport.onUploadConflictingFile).toEqual(jasmine.any(Function))

