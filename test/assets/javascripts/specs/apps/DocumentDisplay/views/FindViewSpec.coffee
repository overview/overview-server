define [
  'backbone'
  'apps/DocumentDisplay/views/FindView'
  'i18n'
], (Backbone, FindView, i18n) ->
  describe 'apps/DocumentDisplay/views/FindView', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.Document.show.FindView',
        label: 'label,{0},{1},{2}'
        previousHighlight: 'previousHighlight'
        nextHighlight: 'nextHighlight'

      @model = new Backbone.Model
        text: 'foo'
        highlights: null
        highlightsQuery: null
        highlightsIndex: null
        highlightsError: null

      @subject = new FindView(model: @model)
      @$el = @subject.$el

    it 'should be hidden while loading text', ->
      @model.set
        text: null
        highlights: [[1,2]]
        highlightsQuery: 'foo'
        highlightsIndex: 0

      expect(@$el).to.have.class('hidden')

    it 'should be hidden if there is an error', ->
      @model.set
        highlights: [[1,2]]
        highlightsQuery: 'foo'
        highlightsIndex: 0
        highlightsError: 'foo'

      expect(@$el).to.have.class('hidden')

    it 'should be hidden while loading highlights', ->
      expect(@$el).to.have.class('hidden')

    describe 'after loading highlights', ->
      beforeEach ->
        @model.set
          text: '12345678900987654321'
          highlights: [[1,4],[11,14],[16,19]]
          highlightsQuery: 'foo'
          highlightsIndex: 0
          highlightsError: null

      it 'should not be hidden', ->
        expect(@$el).not.to.have.class('hidden')

      it 'should have a label', ->
        expect(@subject.$('.label')).to.have.text('label,foo,1,3')

      it 'should do nextHighlight', ->
        @subject.$('.next-highlight').click()
        expect(@subject.$('.label')).to.have.text('label,foo,2,3')

      it 'should do previousHighlight', ->
        @subject.$('.next-highlight').click()
        @subject.$('.previous-highlight').click()
        expect(@subject.$('.label')).to.have.text('label,foo,1,3')

      it 'should disable previousHighlight when index=0', ->
        expect(@subject.$('.previous-highlight')).to.have.class('disabled')
        @subject.$('.previous-highlight').click()
        expect(@model.get('highlightsIndex')).to.eq(0)

      it 'should disable nextHighlight when index=max', ->
        @subject.$('.next-highlight').click()
        @subject.$('.next-highlight').click()
        expect(@subject.$('.next-highlight')).to.have.class('disabled')
        @subject.$('.next-highlight').click()
        expect(@model.get('highlightsIndex')).to.eq(2)
