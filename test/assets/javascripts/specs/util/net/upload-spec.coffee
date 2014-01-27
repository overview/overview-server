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

    describe 'starting an upload with a unicode filename', ->
      fakeStartUploadWithFilename = (filename) ->
        # If the filename isn't an HTTP "token", we UTF-8-escape it
        fakeFile.name = filename
        upload = new Upload(fakeFile, '/upload/')
        mockUploadXhr()
        upload.start()
        mostRecentAjaxRequest().response(status: 404) # not found, go ahead and upload

      mostRecentContentDisposition = ->
        request = mostRecentAjaxRequest()
        request.requestHeaders['Content-Disposition']

      it 'starts the upload, and properly unicode-escapes the filename', ->
        fakeStartUploadWithFilename('元気なですか？.pdf') # filename with unicode
        request = mostRecentAjaxRequest()

        expect(upload.state).toEqual(3)
        expect(request.method).toEqual('POST')
        expect(request.requestHeaders['Content-Disposition']).toEqual("attachment; filename*=UTF-8''%E5%85%83%E6%B0%97%E3%81%AA%E3%81%A7%E3%81%99%E3%81%8B%EF%BC%9F.pdf")

      it 'escapes an even slightly not-HTTP-friendly filename', ->
        fakeStartUploadWithFilename('file,name.txt')
        expect(mostRecentContentDisposition()).toEqual("attachment; filename*=UTF-8''file%2Cname.txt")

      it 'escapes letters encodeURIComponent does not', ->
        fakeStartUploadWithFilename("file'name.txt")
        expect(mostRecentContentDisposition()).toEqual("attachment; filename*=UTF-8''file%27name.txt")

      it 'escapes the asterix', ->
        fakeStartUploadWithFilename("file*name.txt")
        expect(mostRecentContentDisposition()).toEqual("attachment; filename*=UTF-8''file%2Aname.txt")

      it 'does not escape the pipe, caret or backtick', ->
        fakeStartUploadWithFilename("file*|^`name.txt") # trigger encoding
        expect(mostRecentContentDisposition()).toEqual("attachment; filename*=UTF-8''file%2A|^`name.txt")

      it 'simply quotes complex, no-escaping-necessary characters', ->
        fakeStartUploadWithFilename("file|^`name.txt")
        expect(mostRecentContentDisposition()).toEqual('attachment; filename="file|^`name.txt"')

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
        expect(mostRecentAjaxRequest().url).toMatch(makeUUID('foo bar "baz".pdf::last-modified-date::1000'))

      it 'attempts to find the file before uploading', ->
        expect(mostRecentAjaxRequest().method).toEqual('HEAD')

      describe 'when the file is not present on the server yet', ->
        beforeEach ->
          mostRecentAjaxRequest().response(status: 404)  # not found, go ahead and upload

        it 'starts the upload', ->
          request = mostRecentAjaxRequest()

          expect(upload.state).toEqual(3)
          expect(request.method).toEqual('POST')

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

