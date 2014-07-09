define [
  'apps/DocumentDisplay/models/Document'
], (Document) ->
  describe 'apps/DocumentDisplay/models/Document', ->
    it 'should set id, url, title and description', ->
      document = new Document
        id: 3
        url: 'http://example.org'
        title: 'title'
        description: 'description'
      expect(document.id).to.eq(3)
      expect(document.url).to.eq('http://example.org')
      expect(document.title).to.eq('title')
      expect(document.description).to.eq('description')

    it 'should set heading to title if available', ->
      document = new Document
        title: 'title'
        description: 'description'
        urlProperties: {}

      expect(document.heading).to.eq('title')

    it 'should set heading to description if there is no title', ->
      document = new Document
        description: 'description'
        urlProperties: {}

      expect(document.heading).to.eq('description')

    it 'should set heading to description if there is an empty title', ->
      document = new Document
        title: ''
        description: 'description'
        urlProperties: {}

      expect(document.heading).to.eq('description')

    it 'should set heading to "" if title and description are empty', ->
      document = new Document(urlProperties: {})
      expect(document.heading).to.eq('')

    it 'should make two Documents equal if their IDs are equal', ->
      d1 = new Document(id: 3, title: 'blah', url: 'http://example.org')
      d2 = new Document(id: 3, description: 'something else', url: 'http://example2.org')
      expect(d1.equals(d2)).to.be.true
      expect(d2.equals(d1)).to.be.true

    it 'should make two Documents unequal if their IDs are unequal', ->
      d1 = new Document(id: 1, title: 'foo')
      d2 = new Document(id: 2, title: 'foo')
      expect(d1.equals(d2)).to.be.false
      expect(d2.equals(d1)).to.be.false

    describe 'text()', ->
      beforeEach ->
        @sandbox = sinon.sandbox.create(useFakeServer: true, useFakeTimers: true)
        @document = new Document
          id: 3

        @textPromise = @document.getText()
        @textPromise.then(@thenSpy = sinon.spy(), @catchSpy = sinon.spy())
        @sandbox.clock.tick(1)

      afterEach ->
        @sandbox.clock.tick(1)
        for req in @sandbox.server.requests when !req.status?
          req.respond(500, {}, '')
        @sandbox.clock.tick(1)
        @sandbox.restore()

      it 'should return a Promise', -> expect(@textPromise).to.respondTo('then')

      it 'should send a request for text', ->
        r = @sandbox.server.requests[0]
        expect(r.method).to.eq('GET')
        expect(r.url).to.eq('/documents/3.txt')
        expect(r.requestHeaders.Accept).to.contain('text/plain')

      it 'should return the same Promise from every getText()', ->
        expect(@document.getText()).to.eq(@textPromise)

      it 'should call .then() with the text', ->
        @sandbox.server.requests[0].respond(200, { 'Content-Type': 'text/plain' }, 'foo')
        @sandbox.clock.tick(1)
        expect(@thenSpy).to.have.been.calledWith('foo')

      it 'should call .then() with an error', ->
        @sandbox.server.requests[0].respond(500, { 'Content-Type': 'text/plain' }, 'no')
        @sandbox.clock.tick(1)
        expect(@catchSpy).to.have.been.called
        expect(@catchSpy.lastCall.args[0].responseText).to.eq('no')
