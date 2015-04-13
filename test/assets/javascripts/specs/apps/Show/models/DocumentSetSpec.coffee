define [
  'jquery'
  'backbone'
  'apps/Show/models/DocumentSet'
  'i18n'
], ($, Backbone, DocumentSet, i18n) ->
  class Tag extends Backbone.Model

  class TransactionQueue
    ajax: (options) -> $.ajax(options)

  describe 'apps/Show/models/DocumentSet', ->
    beforeEach ->
      i18n.reset_messages
        'views.DocumentSet.show.DocumentListParams.all': 'hack'

      @sandbox = sinon.sandbox.create(useFakeServer: true)
      @transactionQueue = new TransactionQueue()
      @subject = new DocumentSet(12, @transactionQueue)
      @respondJson = (code, data) =>
        req = @sandbox.server.requests[@sandbox.server.requests.length - 1]
        req.respond(code, { 'Content-Type': 'application/json' }, JSON.stringify(data))

    afterEach -> @sandbox.restore()

    it 'should have tags', -> expect(@subject.tags.models).to.deep.eq([])
    it 'should give tags the proper URL', -> expect(@subject.tags.url).to.eq('/documentsets/12/tags')
    it 'should have views', -> expect(@subject.views.models).to.deep.eq([])

    it 'should request stuff', ->
      req = @sandbox.server.requests[0]
      expect(req.method).to.eq('GET')
      expect(req.url).to.eq('/documentsets/12.json')

    it 'should fill in tags', ->
      json = [ { id: 258, name: 'Some tag', color: '#612345' } ]
      @respondJson(200, tags: json)
      expect(@subject.tags.toJSON()).to.deep.eq(json)

    it 'should fill in views', ->
      json = [ { id: 257, title: 'View', creationData: [] } ]
      @respondJson(200, views: json)
      expect(@subject.views.pluck('title')).to.deep.eq([ 'View' ])

    it 'should have documentListParams', ->
      expect(@subject.documentListParams(null).documentSet).to.eq(@subject)

    it 'should tag on the server', ->
      tag = new Tag(id: 2)
      @subject.tag(tag, foo: 'bar')
      req = @sandbox.server.requests[1]
      expect(req.method).to.eq('POST')
      expect(req.url).to.eq('/documentsets/12/tags/2/add')
      expect(req.requestBody).to.eq('foo=bar')

    it 'should untag on the server', ->
      tag = new Tag(id: 2)
      @subject.untag(tag, foo: 'bar')
      req = @sandbox.server.requests[1]
      expect(req.method).to.eq('POST')
      expect(req.url).to.eq('/documentsets/12/tags/2/remove')
      expect(req.requestBody).to.eq('foo=bar')

    it 'should only tag once the Tag has been created', ->
      tag = new Tag(name: 'foo')
      @subject.tag(tag, foo: 'bar')
      expect(@sandbox.server.requests).to.have.length(1)
      tag.set(id: 3)
      tag.trigger('sync')
      expect(@sandbox.server.requests).to.have.length(2)
      req = @sandbox.server.requests[1]
      expect(req.url).to.eq('/documentsets/12/tags/3/add')

    it 'should only untag once the Tag has been created', ->
      tag = new Tag(name: 'foo')
      @subject.untag(tag, foo: 'bar')
      expect(@sandbox.server.requests).to.have.length(1)
      tag.set(id: 3)
      tag.trigger('sync')
      expect(@sandbox.server.requests).to.have.length(2)
      req = @sandbox.server.requests[1]
      expect(req.url).to.eq('/documentsets/12/tags/3/remove')

    it 'should emit "tag"', ->
      tag = new Tag(id: 2)
      @subject.on('tag', spy = sinon.spy())
      @subject.tag(tag, foo: 'bar')
      expect(spy).to.have.been.calledWith(tag, foo: 'bar')

    it 'should emit "untag"', ->
      tag = new Tag(id: 2)
      @subject.on('untag', spy = sinon.spy())
      @subject.untag(tag, foo: 'bar')
      expect(spy).to.have.been.calledWith(tag, foo: 'bar')
