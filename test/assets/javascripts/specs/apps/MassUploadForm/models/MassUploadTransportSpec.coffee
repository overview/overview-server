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
      @sandbox = sinon.sandbox.create(useFakeServer: true)
      progressSpy = sinon.spy()
      successSpy = sinon.spy()
      errorSpy = sinon.spy()
      transport = MassUploadTransport(url: '/files', csrfToken: 'a_token')

    afterEach ->
      @sandbox.restore()

    describe 'doListFiles', ->
      it 'makes an ajax request for the /files endpoint', ->
        transport.doListFiles(progressSpy)
        expect(@sandbox.server.requests[0].url).to.eq('/files')
        expect(@sandbox.server.requests[0].method).to.eq('GET')

      it 'calls the progress callback when it gets data', ->
        deferred = new $.Deferred
        @sandbox.stub($, 'ajax').returns(deferred)
        transport.doListFiles(progressSpy)
        expect(progressSpy).not.to.have.been.called
        deferred.notify({ lengthComputable: true, total: 10, loaded: 5})
        expect(progressSpy).to.have.been.calledWith({total: 10, loaded: 5})

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
        @sandbox.server.requests[0].respond(200, { 'Content-Type': 'application/json' }, JSON.stringify(ajaxResponse))

        expect(successSpy).to.have.been.calledWith( [
          name: 'some-file.pdf',
          loaded: 8
          total: 10
          lastModifiedDate: new Date('2013-10-01T12:19:01Z')
        ] )

      it 'calls the error callback', ->
        transport.doListFiles((() ->), (() ->), errorSpy)
        @sandbox.server.requests[0].respond(404, {}, '')
        expect(errorSpy).to.have.been.called

    describe 'doUploadFile', ->
      file = undefined
      uploadSpy = undefined
      upload = undefined

      beforeEach ->
        file = {
          name: 'foo.pdf'
          lastModifiedDate: new Date('2013-10-01T12:00:00Z')
        }
        uploadSpy = @sandbox.spy(Upload.prototype, 'start')

        transport.doUploadFile(file, progressSpy, successSpy, errorSpy)
        upload = uploadSpy.lastCall.thisValue
        undefined

      it 'uploads the file', ->
        transport.doUploadFile(file)
        expect(uploadSpy).to.have.been.called
        upload = uploadSpy.lastCall.thisValue
        expect(upload.url).to.match(/\/files\/\w+/)
        expect(upload.file).to.be(file)

      it 'calls the progress callback while uploading', ->
        upload.deferred.notify({ state: 'uploading', total: 10, loaded: 5})
        expect(progressSpy).to.have.been.calledWith({total: 10, loaded: 5})

      it 'calls the success callback while uploading', ->
        upload.deferred.resolve()
        expect(successSpy).to.have.been.called

      it 'calls the error callback on error', ->
        upload.deferred.reject()
        expect(errorSpy).to.have.been.called

      it 'includes the CSRF token in the url', ->
        transport.doUploadFile(file)
        expect(uploadSpy).to.have.been.called
        upload = uploadSpy.lastCall.thisValue
        expect(upload.url).to.match(/[\?&]csrfToken=a_token/)

      it 'returns an abort callback', ->
        expect(transport.doUploadFile(file)).to.be.an.instanceOf(Function)

    describe 'doDeleteFile', ->
      it 'exists, but does not do anything for now', ->
        expect(transport.doDeleteFile).to.be.an.instanceOf(Function)

    describe 'onUploadConflicting', ->
      it 'exists, but does not do anything for now', ->
        expect(transport.onUploadConflictingFile).to.be.an.instanceOf(Function)

