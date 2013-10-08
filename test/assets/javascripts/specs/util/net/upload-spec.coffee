define [
  'util/net/upload'
], (Upload) ->
  describe 'util/net/Upload', ->
    fakeFile = undefined
    upload = undefined

    mockUploadXhr = ->
      spyOn(upload.options, 'xhr_factory').andCallFake (callback) ->
        # this is messy.
        xhr = new FakeXMLHttpRequest()
        ajaxRequests.push(xhr)
        xhr

    beforeEach ->
      clearAjaxRequests()

      fakeFile = jasmine.createSpyObj('file', ['lastModifiedDate'])
      fakeFile.name = 'foo.pdf'
      fakeFile.size = 10
      fakeFile.lastModifiedDate = {
        toString: jasmine.createSpy().andReturn('last-modified-date')
      }
      fakeFile.slice = jasmine.createSpy().andReturn('a file blob')

      upload = new Upload(fakeFile, '/upload/')
      mockUploadXhr()

      upload.start()

    it 'moves into the starting state', ->
      expect(upload.state).toEqual(2)

    # TODO: test stop, abort, etc.

    it 'sets the url from the stub that was passed in', ->
      expect(mostRecentAjaxRequest().url).toMatch(/^\/upload\//)

    it 'computes a correct guid for the file', ->
      expect(mostRecentAjaxRequest().url).toMatch(/aa80ee16-e030-3d4f-8269-04a383f75027/) #GUID for the fakeFile's metadata

    it 'attempts to find the file before uploading', ->
      expect(mostRecentAjaxRequest().method).toEqual('HEAD')

    it 'starts the upload and sets the correct Content-Disposition header', ->
      mostRecentAjaxRequest().response(status: 404)  # not found, go ahead and upload
      request = mostRecentAjaxRequest()

      expect(upload.state).toEqual(3)
      expect(request.method).toEqual('POST')
      expect(request.requestHeaders['Content-Disposition']).toEqual('attachment; filename=foo.pdf; modification-date="last-modified-date"')

