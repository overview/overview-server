define [
  'underscore'
  'backbone'
  'apps/Tree/views/DocumentListTitle'
  'apps/Tree/models/observable'
  'i18n'
], (_, Backbone, DocumentListTitle, observable, i18n) ->
  class Tag extends Backbone.Model
    defaults:
      name: 'tag'

  class SearchResult extends Backbone.Model
    defaults:
      query: 'query'
      state: 'InProgress' # or Complete, or Error

  class Node extends Backbone.Model
    defaults:
      description: 'node'

  class DocumentList extends Backbone.Model
    defaults:
      length: null

    initialize: (attributes, options) ->
      @params = options.params

  describe 'apps/Tree/views/DocumentListTitleView', ->
    beforeEach ->
      i18n.reset_messages
        'views.Tree.show.DocumentListTitle.num_documents': 'num_documents,{0}'
        'views.Tree.show.DocumentListTitle.loading': 'loading'
        'views.Tree.show.DocumentListTitle.searchResult.Complete.title_html': 'searchResult.Complete.title_html,{0},{1}'
        'views.Tree.show.DocumentListTitle.searchResult.InProgress.title_html': 'searchResult.InProgress.title_html,{0}'
        'views.Tree.show.DocumentListTitle.searchResult.Error.title_html': 'searchResult.Error.title_html,{0}'
        'views.Tree.show.DocumentListTitle.all.title_html': 'all.title_html,{0}'
        'views.Tree.show.DocumentListTitle.tag.title_html': 'tag.title_html,{0},{1}'
        'views.Tree.show.DocumentListTitle.tag.edit': 'tag.edit'
        'views.Tree.show.DocumentListTitle.untagged.title_html': 'untagged.title_html,{0}'
        'views.Tree.show.DocumentListTitle.node.title_html': 'node.title_html,{0},{1}'
        'views.Tree.show.DocumentListTitle.node.edit': 'node.edit'
        'views.Tree.show.DocumentListTitle.searchResult.title_html': 'searchResult.title_html,{0},{1}'

      @view = new DocumentListTitle(documentList: null)

    afterEach ->
      @view.remove()

    it 'should render nothing with an undefined list', ->
      expect(@view.$el.html()).to.eq('')

    it 'should render loading message with an unloaded list', ->
      @view.setDocumentList(new DocumentList({ length: null }, {
        params:
          type: 'all'
          toI18n: -> [ 'all' ]
      }))
      expect(@view.$el.text()).to.match(/loading/)

    describe 'with a Tag', ->
      beforeEach ->
        @tag = new Tag(name: 'foo')
        @view.setDocumentList(new DocumentList({ length: 4 }, {
          params:
            type: 'tag'
            params: [ @tag ]
            toI18n: => [ 'tag', @tag.attributes.name ]
        }))

      it 'should render the title', -> expect(@view.$('h4').text()).to.eq('tag.title_html,num_documents,4,foo')
      it 'should trigger edit-tag', ->
        @view.on('edit-tag', spy = sinon.spy())
        $a = @view.$('a.edit')
        expect($a.length).to.eq(1)
        $a.click()
        expect(spy).to.have.been.calledWith(@tag)

      it 'should listen for tag title changes', ->
        @tag.set(name: 'bar')
        expect(@view.$('h4').text()).to.eq('tag.title_html,num_documents,4,bar')

    it 'should render the title when selecting untagged', ->
      @view.setDocumentList(new DocumentList({ length: 4 }, {
        params:
          type: 'untagged'
          toI18n: -> [ 'untagged' ]
      }))
      expect(@view.$('h4').text()).to.eq('untagged.title_html,num_documents,4')

    describe 'with a Node', ->
      beforeEach ->
        @node = new Node(description: 'foo')
        @view.setDocumentList(new DocumentList({ length: 4 }, {
          params:
            type: 'node'
            params: [ @node ]
            toI18n: => [ 'node', @node.attributes.description ]
        }))

      it 'should render the title', ->
        expect(@view.$('h4').text()).to.eq('node.title_html,num_documents,4,foo')

      it 'should trigger edit-node', ->
        @view.on('edit-node', spy = sinon.spy())
        $a = @view.$('a.edit')
        expect($a.length).to.eq(1)
        $a.click()
        expect(spy).to.have.been.calledWith(@node)

      it 'should adjust title after changed', ->
        @node.set(description: 'bar')
        expect(@view.$('h4').text()).to.eq('node.title_html,num_documents,4,bar')

    describe 'with a SearchResult', ->
      beforeEach ->
        @searchResult = new SearchResult(query: 'query')
        @view.setDocumentList(new DocumentList({ length: null }, {
          params:
            type: 'searchResult'
            params: [ @searchResult ]
            toI18n: => [ 'searchResult', @searchResult.attributes.query ]
        }))

      it 'should render searching message', ->
        expect(@view.$el.text()).to.match(/searchResult.InProgress/)

      it 'should still render search message when search is halfway done', ->
        @view.documentList.set(length: 4)
        expect(@view.$el.text()).to.match(/searchResult.InProgress/)

      it 'should have class=search-inprogress', ->
        expect(@view.$el.attr('class')).to.eq('search-inprogress loading')

      describe 'when complete', ->
        beforeEach ->
          @view.documentList.set(length: 4)
          @searchResult.set(state: 'Complete')

        it 'should render the title', ->
          expect(@view.$('h4').text()).to.eq('searchResult.Complete.title_html,num_documents,4,query')

        it 'should have class=search-complete', ->
          expect(@view.$el.attr('class')).to.eq('search-complete loaded')

        it 'should show in-progress if the length is null', ->
          @view.documentList.set(length: null)
          expect(@view.$('h4').text()).to.eq('searchResult.InProgress.title_html,query')

      describe 'on error', ->
        beforeEach ->
          @searchResult.set(state: 'Error')

        it 'should render an error', ->
          expect(@view.$('h4').text()).to.eq('searchResult.Error.title_html,query')

        it 'should have class=search-error', ->
          expect(@view.$el.attr('class')).to.eq('search-error loaded')
