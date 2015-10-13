define [
  'backbone'
  'apps/MassUploadForm/models/MassUploadTransport',
  'util/net/upload'
], (Backbone, MassUploadTransport, NetUpload) ->
  describe 'apps/MassUploadForm/models/MassUploadTransport', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true)
      @progressSpy = sinon.spy()
      @doneSpy = sinon.spy()
      @transport = MassUploadTransport(url: '/files', csrfToken: 'a_token')

    afterEach ->
      @sandbox.restore()

    describe 'doListFiles', ->
      beforeEach ->
        @transport.doListFiles(@progressSpy, @doneSpy)
        undefined

      it 'makes an ajax request for the /files endpoint', ->
        expect(@sandbox.server.requests[0].url).to.eq('/files')
        expect(@sandbox.server.requests[0].method).to.eq('GET')

      it 'calls the progress callback when it gets data', ->
        deferred = new $.Deferred
        @sandbox.stub($, 'ajax').returns(deferred)
        @transport.doListFiles(@progressSpy, @doneSpy)
        expect(@progressSpy).not.to.have.been.called
        deferred.notify({ lengthComputable: true, total: 10, loaded: 5})
        expect(@progressSpy).to.have.been.calledWith({total: 10, loaded: 5})

      it 'calls the success callback when the upload finishes', ->
        ajaxResponse =
          files: [
            guid: 'foo'
            name: 'some-file.pdf'
            lastModifiedDate: '2013-10-01T12:19:01Z'
            size: 10
            uploadedSize: 8
          ]
        @sandbox.server.requests[0].respond(200, { 'Content-Type': 'application/json' }, JSON.stringify(ajaxResponse))

        expect(@doneSpy).to.have.been.calledWith(null, [
          name: 'some-file.pdf',
          loaded: 8
          total: 10
          lastModifiedDate: new Date('2013-10-01T12:19:01Z')
        ])

      it 'calls the error callback', ->
        @sandbox.server.requests[0].respond(404, {}, '')
        expect(@doneSpy).to.have.been.called
        expect(@doneSpy.lastCall.args[0]).to.be.an.instanceOf(Error)

    describe 'doUploadFile', ->
      describe 'normally', ->
        beforeEach ->
          @file =
            name: 'foo.pdf'
            lastModifiedDate: new Date('2015-05-11T19:26:50Z')
          @upload = new Backbone.Model(file: @file)
          netUploadSpy = @sandbox.spy(NetUpload.prototype, 'start')

          @abort = @transport.doUploadFile(@upload, @progressSpy, @doneSpy)
          @netUpload = netUploadSpy.lastCall.thisValue
          undefined

        it 'uploads the file', ->
          expect(@netUpload).to.exist
          expect(@netUpload.url).to.match(/\/files\/\w+/)
          expect(@netUpload.file).to.eq(@file)

        it 'includes the CSRF token in the url', ->
          expect(@netUpload.url).to.match(/[\?&]csrfToken=a_token/)

        it 'calls the progress callback while uploading', ->
          @netUpload.deferred.notify({ state: 'uploading', total: 10, loaded: 5})
          expect(@progressSpy).to.have.been.calledWith({total: 10, loaded: 5})

        it 'calls the success callback while uploading', ->
          @netUpload.deferred.resolve()
          expect(@doneSpy).to.have.been.calledWith(null)

        it 'calls the error callback on error', ->
          error = new Error('some error')
          @netUpload.deferred.reject(error)
          expect(@doneSpy).to.have.been.calledWith(error)

        it 'returns an abort callback', ->
          expect(@abort).to.be.an.instanceOf(Function)

      describe 'with a uniqueCheckUrlPrefix', ->
        beforeEach ->
          @file = new Blob(['foo bar baz'])
          @file.name = 'foo.pdf'
          @file.lastModifiedDate = new Date('2013-10-01T12:00:00Z')
          @upload = new Backbone.Model(file: @file)
          @netUploadSpy = @sandbox.spy(NetUpload.prototype, 'start')

          @transport = MassUploadTransport
            url: '/files'
            csrfToken: 'a_token'
            uniqueCheckUrlPrefix: '/exists'
          @abort = @transport.doUploadFile(@upload, @progressSpy, @doneSpy)
          undefined

        it 'checks the server for a matching sha1', (done) ->
          setTimeout(=>
            expect(@netUploadSpy).not.to.have.been.called
            expect(@sandbox.server.requests).to.have.length(1)
            expect(@sandbox.server.requests[0].url).to.eq('/exists/c7567e8b39e2428e38bf9c9226ac68de4c67dc39')
            done()
          , 1)

        it 'uploads when the file passes the uniqueness check', (done) ->
          setTimeout(=>
            @sandbox.server.requests[0].respond(404, {}, 'file does not exist')
            expect(@netUploadSpy).to.have.been.called
            expect(@doneSpy).not.to.have.been.called
            done()
          , 1)

        it 'succeeds without uploading when the server has the file already', (done) ->
          setTimeout(=>
            @sandbox.server.requests[0].respond(204, {}, 'file exists')
            expect(@netUploadSpy).not.to.have.been.called
            expect(@upload.get('skippedBecauseAlreadyInDocumentSet')).to.eq(true)
            expect(@doneSpy).to.have.been.calledWith(null)
            done()
          , 1)

        it 'skips uploading when aborting during existence check', (done) ->
          @abort()
          setTimeout(=>
            @sandbox.server.requests[0].respond(404, {}, 'file does not exist')
            expect(@netUploadSpy).not.to.have.been.called
            expect(@doneSpy).to.have.been.calledWith(null)
            done()
          , 1)

    describe 'doDeleteFile', ->
      it 'exists, but does not do anything for now', ->
        expect(@transport.doDeleteFile).to.be.an.instanceOf(Function)

    describe 'onUploadConflicting', ->
      it 'exists, but does not do anything for now', ->
        expect(@transport.onUploadConflictingFile).to.be.an.instanceOf(Function)

