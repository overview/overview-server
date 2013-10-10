define [
  'util/net/upload'
  'md5'
], (Upload, md5) ->
  describe 'util/net/Upload', ->
    fakeFile = undefined
    upload = undefined
    md5 = CryptoJS.MD5

    mockUploadXhr = ->
      spyOn(upload.options, 'xhr_factory').andCallFake (callback) ->
        # this is messy.
        xhr = new FakeXMLHttpRequest()
        ajaxRequests.push(xhr)
        xhr

    makeUUID = (inputString) ->
      hash = md5(inputString).toString()
      parts = []
      parts.push(hash[0...8])
      parts.push(hash[8...12])
      parts.push('3' + hash[13...16])
      y = (parseInt(hash[16...18], 16) & 0x3f | 0x80).toString(16)
      parts.push(y + hash[18...20])
      parts.push(hash[20...32])
      parts.join('-')

    beforeEach ->
      clearAjaxRequests()

      fakeFile = jasmine.createSpyObj('file', ['lastModifiedDate'])
      fakeFile.name = 'foo bar "baz".pdf'  # filename with spaces and quotes
      fakeFile.size = 1000
      fakeFile.lastModifiedDate = {
        toString: jasmine.createSpy().andReturn('last-modified-date')
      }
      fakeFile.slice = jasmine.createSpy().andReturn('a file blob')

    # TODO: test stop, abort, etc.

    describe 'starting an upload', ->
      beforeEach ->
        upload = new Upload(fakeFile, '/upload/')
        mockUploadXhr()
        upload.start()

      it 'moves into the starting state', ->
        expect(upload.state).toEqual(2)

      it 'sets the url from the stub that was passed in', ->
        expect(mostRecentAjaxRequest().url).toMatch(/^\/upload\//)

      it 'computes a correct guid for the file', ->
        expect(mostRecentAjaxRequest().url).toMatch(makeUUID('foo bar baz.pdf::last-modified-date::1000'))

      it 'attempts to find the file before uploading', ->
        expect(mostRecentAjaxRequest().method).toEqual('HEAD')

      describe 'when the file is not present on the server yet', ->
        beforeEach ->
          mostRecentAjaxRequest().response(status: 404)  # not found, go ahead and upload

        it 'starts the upload, and filters quotes from the filename', ->
          request = mostRecentAjaxRequest()

          expect(upload.state).toEqual(3)
          expect(request.method).toEqual('POST')
          expect(request.requestHeaders['Content-Disposition']).toEqual('attachment; filename="foo bar baz.pdf"')

        it 'correctly specifies the content-range', ->
          expect(mostRecentAjaxRequest().requestHeaders['Content-Range']).toEqual('0-999/1000')

      describe 'when part of the file has been uploaded already', ->
        beforeEach ->
          upload = new Upload(fakeFile, '/upload/')
          mockUploadXhr()
          upload.start()
          mostRecentAjaxRequest().response(
            status: 404
            responseHeaders:
              'Content-Type': 'application/json'
              'Content-Range': '0-499/1000'
          )

        it 'correctly specifies the content-range', ->
          expect(mostRecentAjaxRequest().requestHeaders['Content-Range']).toEqual('500-999/1000')

    describe 'with a zero-length file', ->
      beforeEach ->
        fakeFile.size = 0
        upload = new Upload(fakeFile, '/upload/')
        mockUploadXhr()
        upload.start()
        mostRecentAjaxRequest().response(status: 404)  # not found, go ahead and upload

      it 'correctly specifies the content-range', ->
        expect(mostRecentAjaxRequest().requestHeaders['Content-Range']).toEqual('0-0/0')

