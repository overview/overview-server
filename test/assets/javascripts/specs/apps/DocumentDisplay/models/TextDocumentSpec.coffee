define [
  'backbone'
  'apps/DocumentDisplay/models/TextDocument'
], (Backbone, TextDocument) ->
  describe 'apps/DocumentDisplay/models/TextDocument', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true)
      @subject = new TextDocument(id: 3, documentSetId: 123)

    afterEach ->
      @sandbox.restore()

    it 'should fetch text from the correct path', ->
      @subject.fetchText()
      expect(@sandbox.server.requests).to.have.length(1)
      expect(@sandbox.server.requests[0].url).to.eq('/documents/3.txt')

    it 'should fetch highlights from the correct path', ->
      @subject.fetchHighlights('foo')
      expect(@sandbox.server.requests).to.have.length(1)
      expect(@sandbox.server.requests[0].url).to.eq('/documentsets/123/documents/3/highlights?q=foo')

    it 'should escape the query in the highlight URL', ->
      @subject.fetchHighlights('/foo.+/')
      expect(@sandbox.server.requests[0].url).to.eq('/documentsets/123/documents/3/highlights?q=%2Ffoo.%2B%2F')

    it 'should start with text+error null', ->
      @subject.fetchText()
      expect(@subject.get('text')).to.be.null
      expect(@subject.get('error')).to.be.null

    it 'should set text on success', ->
      @subject.fetchText()
      @sandbox.server.requests[0].respond(200, { 'Content-Type': 'text/plain; charset=utf-8' }, 'foo bar')
      expect(@subject.get('text')).to.eq('foo bar')
      expect(@subject.get('error')).to.be.null

    it 'should set error on failure', ->
      @subject.fetchText()
      @sandbox.stub(console, 'warn')
      @sandbox.server.requests[0].respond(500, {}, 'some error')
      expect(@subject.get('text')).to.be.null
      expect(@subject.get('error')).not.to.be.null

    it 'should start with highlight stuff null', ->
      expect(@subject.get('highlightsQuery')).to.be.null
      @subject.fetchHighlights('foo')
      expect(@subject.get('highlightsQuery')).to.eq('foo')
      expect(@subject.get('highlights')).to.be.null
      expect(@subject.get('highlightsError')).to.be.null

    it 'should set highlights on success', ->
      @subject.fetchHighlights('foo')
      @sandbox.server.requests[0].respond(200, { 'Content-Type': 'application/json' }, '[[1,2],[3,4]]')
      expect(@subject.get('highlightsQuery')).to.eq('foo')
      expect(@subject.get('highlights')).to.deep.eq([[1,2],[3,4]])
      expect(@subject.get('highlightsError')).to.be.null

    it 'should set highlightsError on error', ->
      @subject.fetchHighlights('foo')
      @sandbox.stub(console, 'warn')
      @sandbox.server.requests[0].respond(500, {}, 'some error')
      expect(@subject.get('highlightsQuery')).to.eq('foo')
      expect(@subject.get('highlights')).to.be.null
      expect(@subject.get('highlightsError')).not.to.be.null

    it 'should not fetch text twice', ->
      @subject.fetchText()
      @subject.fetchText()
      expect(@sandbox.server.requests).to.have.length(1)

    it 'should not fetch highlights twice with the same query', ->
      @subject.fetchHighlights('foo')
      @subject.fetchHighlights('foo')
      expect(@sandbox.server.requests).to.have.length(1)

    it 'should ignore the first highlight request when a second request has been sent', ->
      @sandbox.stub(console, 'warn')
      @subject.fetchHighlights('foo')
      @subject.fetchHighlights('bar')
      try
        @sandbox.server.requests[0].respond(200, { 'Content-Type': 'application/json' }, '[[1,2]]')
      catch e
        # ignore. That can fail because the request may be aborted.
      expect(@subject.get('highlights')).to.be.null

    it 'should nullify everything when un-highlighting', ->
      @sandbox.stub(console, 'warn')
      @subject.fetchHighlights('foo')
      @subject.fetchHighlights(null)
      expect(@sandbox.server.requests).to.have.length(1)
      try
        @sandbox.server.requests[0].respond(200, { 'Content-Type': 'application/json' }, '[[1,2]]')
      catch e
        # ignore. That can fail because the request may be aborted.
      expect(@subject.get('highlightsQuery')).to.be.null
      expect(@subject.get('highlights')).to.be.null
      expect(@subject.get('highlightsError')).to.be.null
