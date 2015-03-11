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

  class State extends Backbone.Model
    defaults:
      oneDocumentSelected: false

  describe 'apps/Show/views/DocumentListTitleView', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.DocumentSet.show.DocumentListTitle',
        'num_documents': 'num_documents,{0}'
        'loading': 'loading'
        'tag.edit': 'tag.edit'
        'node.edit': 'node.edit'
        'list': 'list'
        'one': 'one'

      @state = new State()
      @view = new DocumentListTitle(documentList: null, state: @state)

    afterEach ->
      @view.remove()

    describe 'with an undefined list', ->
      it 'should render nothing', ->
        expect(@view.$('h4').html()).to.eq('')
        expect(@view.$('.edit-link').html()).to.eq('')
        expect(@view.$('input')).to.be.disabled

    describe 'with a loading list', ->
      beforeEach ->
        @view.setDocumentList(new DocumentList({ length: null }, {
          params:
            title: '%s in document set'
            params: {}
        }))

      it 'should render loading message with an unloaded list', ->
        expect(@view.$('h4').text()).to.match(/loading/)

      it 'edit link', ->
        expect(@view.$('a.edit')).not.to.be.empty

      it 'should render a disabled one-document toggle', ->
        expect(@view.$('input')).to.be.disabled

    describe 'with a Tag', ->
      beforeEach ->
        @tag = new Tag(id: 1, name: 'foo')
        @params =
          documentSet: { tags: { get: (id) => if id == 1 then @tag else undefined } }
          title: '%s tagged foo'
          params: { tags: [ @tag.id ] }
          reset:
            byTag: (t) ->
              title: "%s tagged #{t.get('name')}"

        @view.setDocumentList(new DocumentList({ length: 4 }, { params: @params }))

      it 'should render the title', ->
        expect(@view.$('h4').html()).to.eq('<strong>num_documents,4</strong> tagged foo')

      it 'should trigger edit-tag', ->
        @view.on('edit-tag', spy = sinon.spy())
        $a = @view.$('a.edit')
        expect($a.length).to.eq(1)
        $a.click()
        expect(spy).to.have.been.calledWith(@tag)

      it 'should listen for tag title changes', ->
        @tag.set(name: 'bar')
        expect(@view.$('h4').html()).to.eq('<strong>num_documents,4</strong> tagged bar')

      it 'should toggle to one document', ->
        @view.$('input').prop('checked', false).change()
        expect(@state.get('oneDocumentSelected')).to.be.true

      it 'should toggle back to the list', ->
        @view.$('input').prop('checked', false).change()
        @view.$('input').prop('checked', true).change()
        expect(@state.get('oneDocumentSelected')).to.be.false

      it 'should disable the toggle when there are no documents', ->
        @view.setDocumentList(new DocumentList({ length: 0 }, { params: @params }))
        expect(@view.$('input')).to.be.disabled

      it 'should turn on the toggle when state oneDocumentSelected becomes true', ->
        @state.set(oneDocumentSelected: true)
        expect(@view.$('input')).not.to.be.checked

      it 'should turn off the toggle when state oneDocumentSelected becomes false', ->
        @state.set(oneDocumentSelected: false)
        expect(@view.$('input')).to.be.checked

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
