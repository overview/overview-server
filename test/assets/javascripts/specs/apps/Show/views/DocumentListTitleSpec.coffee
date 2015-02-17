define [
  'underscore'
  'backbone'
  'apps/Show/views/DocumentListTitle'
  'apps/Show/models/observable'
  'i18n'
], (_, Backbone, DocumentListTitle, observable, i18n) ->
  class Tag extends Backbone.Model
    defaults:
      name: 'tag'

  class Node extends Backbone.Model
    defaults:
      description: 'node'

  class DocumentList extends Backbone.Model
    defaults:
      length: null

    initialize: (attributes, options) ->
      @params = options.params

  describe 'apps/Show/views/DocumentListTitleView', ->
    beforeEach ->
      i18n.reset_messages
        'views.Tree.show.DocumentListTitle.num_documents': 'num_documents,{0}'
        'views.Tree.show.DocumentListTitle.loading': 'loading'
        'views.Tree.show.DocumentListTitle.tag.edit': 'tag.edit'
        'views.Tree.show.DocumentListTitle.node.edit': 'node.edit'

      @view = new DocumentListTitle(documentList: null)

    afterEach ->
      @view.remove()

    it 'should render nothing with an undefined list', ->
      expect(@view.$el.html()).to.eq('')

    it 'should render loading message with an unloaded list', ->
      @view.setDocumentList(new DocumentList({ length: null }, {
        params:
          title: '%s in document set'
          params: {}
      }))
      expect(@view.$el.text()).to.match(/loading/)

    describe 'with a Tag', ->
      beforeEach ->
        @tag = new Tag(id: 1, name: 'foo')
        @view.setDocumentList(new DocumentList({ length: 4 }, {
          params:
            documentSet: { tags: { get: (id) => if id == 1 then @tag else undefined } }
            title: '%s tagged foo'
            params: { tags: [ 1 ] }
            reset:
              byTag: (t) ->
                title: "%s tagged #{t.get('name')}"
        }))

      it 'should render the title', -> expect(@view.$('h4').html()).to.eq('<strong>num_documents,4</strong> tagged foo')
      it 'should trigger edit-tag', ->
        @view.on('edit-tag', spy = sinon.spy())
        $a = @view.$('a.edit')
        expect($a.length).to.eq(1)
        $a.click()
        expect(spy).to.have.been.calledWith(@tag)

      it 'should listen for tag title changes', ->
        @tag.set(name: 'bar')
        expect(@view.$('h4').html()).to.eq('<strong>num_documents,4</strong> tagged bar')

    describe 'with a Node', ->
      beforeEach ->
        @node = { id: 1, description: 'foo' }
        _.extend(@node, Backbone.Events)
        @view.setDocumentList(new DocumentList({ length: 4 }, {
          params:
            title: '%s with node foo'
            view: { onDemandTree: { getNode: (id) => if id == 1 then @node else undefined } }
            params: { nodes: [ 1 ] }
            reset:
              byNode: (n) ->
                title: "%s with node #{n.description}"
        }))

      it 'should trigger edit-node', ->
        @view.on('edit-node', spy = sinon.spy())
        $a = @view.$('a.edit')
        expect($a.length).to.eq(1)
        $a.click()
        expect(spy).to.have.been.calledWith(@node)

      it 'should listen for node description changes', ->
        @node.description = 'bar'
        @node.trigger('change', @node)
        expect(@view.$('h4').html()).to.eq('<strong>num_documents,4</strong> with node bar')

    describe 'with a search query', ->
      beforeEach ->
        @view.setDocumentList(new DocumentList({ length: null }, {
          params:
            title: 'title'
            params: { q: 'foo' }
        }))

      it 'should render loading message', ->
        expect(@view.$el.text().trim()).to.eq('loading')

      it 'should render the title when done', ->
        @view.documentList.set(length: 4)
        expect(@view.$('h4').text()).to.eq('title')
