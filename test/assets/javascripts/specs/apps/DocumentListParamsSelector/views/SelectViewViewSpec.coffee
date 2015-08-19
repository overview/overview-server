define [
  'backbone'
  'apps/DocumentListParamsSelector/views/SelectViewView'
  'i18n'
], (Backbone, SelectViewView, i18n) ->
  describe 'apps/DocumentListParamsSelector/views/SelectViewView', ->
    factory =
      view: (id, title) -> new Backbone.Model(id: id, title: title)

    beforeEach ->
      i18n.reset_messages_namespaced 'views.DocumentSet.show.DocumentListParamsSelector.SelectViewView',
        inDocumentSet: 'inDocumentSet'
        inView: 'inView,{0}'

      @params = new Backbone.Model
        view: null
        title: ''
        objectIds: []
        nodeIds: []
      @state = new Backbone.Model
        view: null
      @views = new Backbone.Collection([])
      @subject = new SelectViewView(model: @params, state: @state, views: @views)

    it 'should show inDocumentSet by default with no dropdown', ->
      expect(@subject.$el).to.have.class('has-no-view')
      expect(@subject.$el).to.have.text('inDocumentSet')
      expect(@subject.$('ul')).not.to.exist

    describe 'when there is a View in the State', ->
      beforeEach ->
        @view = factory.view(1, 'view1')
        @views.add([ @view ])
        @state.set(view: @view)

      it 'should have inDocumentSet selected', ->
        expect(@subject.$el).to.have.class('has-view')
        expect(@subject.$('a:eq(0)')).to.contain('inDocumentSet')

      it 'should have a view option', ->
        expect(@subject.$('a:eq(1)')).to.have.text('inDocumentSet')
        expect(@subject.$('a:eq(2)')).to.have.text('inView,view1')

      it 'should have the view selected when it becomes selected', ->
        @params.set(view: @view)
        expect(@subject.$('a:eq(0)')).to.contain('inView,view1')

      it 'should select the view when clicking on it', ->
        @subject.$('a:eq(2)').click()
        expect(@params.get('view')).to.eq(@view)

      it 'should select the document set when clicking it', ->
        @subject.$('a:eq(2)').click()
        @subject.$('a:eq(1)').click()
        expect(@params.get('view')).to.be.null

      describe 'when an Object is selected', ->
        beforeEach ->
          @params.set(view: @view, objectIds: [ 1 ], title: '%s in object 1')

        it 'should show the given text', ->
          expect(@subject.$('a:eq(0)')).to.contain('in object 1')
          expect(@subject.$('a:eq(0)')).not.to.contain('%s')

        it 'should include the given text in the dropdown', ->
          expect(@subject.$('a:eq(3)')).to.have.text('in object 1')

        it 'should let the user click away and then click back', ->
          @subject.$('a:eq(1)').click()
          expect(@params.attributes).to.deep.eq(view: null, nodeIds: [], objectIds: [], title: '')
          expect(@subject.$('a:eq(3)')).to.have.text('in object 1') # it doesn't go away
          @subject.$('a:eq(3)').click()
          expect(@params.attributes).to.deep.eq(view: @view, nodeIds: [], objectIds: [ 1 ], title: '%s in object 1')

        it 'should hide duplicate history entries', ->
          @subject.$('a:eq(1)').click()
          @subject.$('a:eq(3)').click()
          expect(@subject.$('a')).to.have.length(4)

        it 'should limit to five history entries', ->
          select = (n) => @params.set(view: @view, objectIds: [ n ], title: "%s in object #{n}")
          select(n) for n in [ 0 ... 6 ]
          expect(@subject.$('a')).to.have.length(8)

      describe 'when a Tag is selected', ->
        beforeEach ->
          @params.set(view: @view, tagIds: [ 1 ], title: '%s with tag 1')

        it 'should not show the tag or title', ->
          expect(@subject.$('a:eq(0)')).to.contain('inView,view1')

        it 'should not include the tag in the history', ->
          expect(@subject.$('a')).to.have.length(3)
