define [
  'jquery'
  'backbone'
  'apps/Show/models/DocumentSet'
], ($, Backbone, DocumentSet) ->
  class Tag extends Backbone.Model

  class DocumentListParams
    constructor: (@props) ->
    toApiParams: -> @props

  class TransactionQueue
    ajax: (optionsCallback) -> $.ajax(optionsCallback())

  describe 'apps/Show/models/DocumentSet', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true)
      @transactionQueue = new TransactionQueue()
      @subject = new DocumentSet(12, @transactionQueue)
      @respondJson = (code, data) =>
        req = @sandbox.server.requests[@sandbox.server.requests.length - 1]
        req.respond(code, { 'Content-Type': 'application/json' }, JSON.stringify(data))

    afterEach -> @sandbox.restore()

    it 'should have nDocuments', -> expect(@subject.nDocuments).to.be.null
    it 'should have tags', -> expect(@subject.tags.models).to.deep.eq([])
    it 'should give tags the proper URL', -> expect(@subject.tags.url).to.eq('/documentsets/12/tags')
    it 'should have searchResults', -> expect(@subject.searchResults.models).to.deep.eq([])
    it 'should give searchResults the proper URL', -> expect(@subject.searchResults.url).to.eq('/documentsets/12/searches')
    it 'should have views', -> expect(@subject.views.models).to.deep.eq([])

    it 'should request stuff', ->
      req = @sandbox.server.requests[0]
      expect(req.method).to.eq('GET')
      expect(req.url).to.eq('/documentsets/12.json')

    it 'should fill in nDocuments', ->
      @respondJson(200, nDocuments: 123)
      expect(@subject.nDocuments).to.eq(123)

    it 'should fill in tags', ->
      json = [ { id: 258, name: 'Some tag', color: '#612345' } ]
      @respondJson(200, tags: json)
      expect(@subject.tags.toJSON()).to.deep.eq(json)

    it 'should fill in searchResults', ->
      json = [ { id: 256, query: 'Query', createdAt: (new Date().toISOString()), state: 'InProgress' } ]
      @respondJson(200, searchResults: json)
      expect(@subject.searchResults.toJSON()).to.deep.eq(json)

    it 'should fill in views', ->
      json = [ { id: 257, title: 'View', creationData: [] } ]
      @respondJson(200, views: json)
      expect(@subject.views.pluck('title')).to.deep.eq([ 'View' ])

    it 'should have documentListParams', ->
      expect(@subject.documentListParams(null).all().documentSet).to.eq(@subject)

    it 'should tag on the server', ->
      tag = new Tag(id: 2)
      params = new DocumentListParams(foo: 'bar')
      @subject.tag(tag, params)
      req = @sandbox.server.requests[1]
      expect(req.method).to.eq('POST')
      expect(req.url).to.eq('/documentsets/12/tags/2/add')
      expect(req.requestBody).to.eq('foo=bar')

    it 'should untag on the server', ->
      tag = new Tag(id: 2)
      params = new DocumentListParams(foo: 'bar')
      @subject.untag(tag, params)
      req = @sandbox.server.requests[1]
      expect(req.method).to.eq('POST')
      expect(req.url).to.eq('/documentsets/12/tags/2/remove')
      expect(req.requestBody).to.eq('foo=bar')

    it 'should emit "tag"', ->
      tag = new Tag(id: 2)
      params = new DocumentListParams(foo: 'bar')
      @subject.on('tag', spy = sinon.spy())
      @subject.tag(tag, params)
      expect(spy).to.have.been.calledWith(tag, params)

    it 'should emit "untag"', ->
      tag = new Tag(id: 2)
      params = new DocumentListParams(foo: 'bar')
      @subject.on('untag', spy = sinon.spy())
      @subject.untag(tag, params)
      expect(spy).to.have.been.calledWith(tag, params)
