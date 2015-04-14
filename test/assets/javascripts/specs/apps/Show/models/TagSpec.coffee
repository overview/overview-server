define [ 'backbone', 'apps/Show/models/Tag' ], (Backbone, Tag) ->
  describe 'apps/Show/models/Tag', ->
    it 'should assign a color by default', ->
      tag = new Tag(name: 'foo')
      expect(tag.get('color')).to.match(/#[0-9a-z]{6}/)

    it 'should not assign the same color to different tags', ->
      tag1 = new Tag(name: 'foo')
      tag2 = new Tag(name: 'bar')
      expect(tag1.get('color')).not.to.eq(tag2.get('color'))

    it 'should get a dark class for a dark color', ->
      tag = new Tag(name: 'foo', color: '#123456')
      expect(tag.getClass()).to.eq('tag tag-dark')

    it 'should get a light class for a light color', ->
      tag = new Tag(name: 'foo', color: '#9ab9ab')
      expect(tag.getClass()).to.eq('tag tag-light')

    it 'should use background-color in getStyle()', ->
      tag = new Tag(name: 'foo', color: '#123456')
      expect(tag.getStyle()).to.eq('background-color: #123456')

    it 'should call whenExists parameter immediately when id is set', ->
      tag = new Tag(id: 1, name: 'foo')
      tag.whenExists(spy = sinon.spy())
      expect(spy).to.have.been.called

    it 'should call whenExists later when id is not set', ->
      tag = new Tag(name: 'foo')
      tag.whenExists(spy = sinon.spy())
      expect(spy).not.to.have.been.called
      tag.id = 3
      tag.trigger('sync')
      expect(spy).to.have.been.called

    describe 'tagging', ->
      beforeEach ->
        @sandbox = sinon.sandbox.create()
        @sandbox.stub(Backbone, 'ajax')

      afterEach ->
        @sandbox.restore()

      it 'should POST to add a document', ->
        tag = new Tag(id: 1, name: 'foo')
        tag.url = '/foo'
        tag.addToDocumentsOnServer(nodes: '2')
        expect(Backbone.ajax).to.have.been.calledWith
          type: 'POST'
          url: '/foo/add'
          data: { nodes: '2' }
          debugInfo: 'Tag.addToDocumentsOnServer'

      it 'should POST to remove a document', ->
        tag = new Tag(id: 1, name: 'foo')
        tag.url = '/foo'
        tag.removeFromDocumentsOnServer(nodes: '2')
        expect(Backbone.ajax).to.have.been.calledWith
          type: 'POST'
          url: '/foo/remove'
          data: { nodes: '2' }
          debugInfo: 'Tag.removeFromDocumentsOnServer'

      it 'should defer POST until the tag exists', ->
        tag = new Tag(name: 'foo')
        tag.addToDocumentsOnServer(nodes: '2')
        tag.removeFromDocumentsOnServer(nodes: '2')
        expect(Backbone.ajax).not.to.have.been.called
        tag.id = 1
        tag.url = '/foo'
        tag.trigger('sync')
        expect(Backbone.ajax).to.have.been.called.twice
