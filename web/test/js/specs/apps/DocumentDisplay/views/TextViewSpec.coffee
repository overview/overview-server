define [
  'jquery'
  'backbone'
  'i18n'
  'apps/DocumentDisplay/views/TextView'
], ($, Backbone, i18n, TextView) ->
  describe 'apps/DocumentDisplay/views/TextView', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.Document.show.TextView',
        loading: 'loading'
        error: 'error'
        onlyTextAvailable: 'onlyTextAvailable'
        isFromOcr_html: 'isFromOcr_html'

      @preferences = new Backbone.Model(wrap: true)
      @$ = (args...) => @subject.$(args...)
      @model = new Backbone.Model
        url: null
        text: null
        error: null
        highlights: null
        highlightsIndex: null
      @$div = $('<div></div>').appendTo('body') # so we can calculate widths
      @subject = new TextView(model: @model, preferences: @preferences, currentCapabilities: @capabilities)
      @subject.$el.appendTo(@$div)

    afterEach ->
      @subject.remove()
      @$div.remove()

    it 'should render a loading message when loading', ->
      expect(@subject.$('.loading')).to.contain('loading')

    it 'should render loading when given null text', ->
      @model.set(text: 'foo')
      @model.set(text: null)
      expect(@subject.$('.loading')).to.contain('loading')

    it 'should render an error message when loading fails', ->
      @model.set(text: null, error: 'foo')
      expect(@subject.$('.error')).to.contain('error')

    it 'should render text when loading succeeds', ->
      @model.set(text: 'foobar')
      expect(@subject.$('pre')).to.contain('foobar')

    it 'should render highlights when they come after text', ->
      @model.set(text: 'foo bar moo mar')
      @model.set(highlights: [[4,7],[12,15]])
      expect(@subject.$('pre').html()).to.eq('foo <em class="highlight">bar</em> moo <em class="highlight">mar</em>')

    it 'should still say loading if highlights come before text', ->
      @model.set(highlights: [[4,7],[12,15]])
      expect(@subject.$('.loading')).to.contain('loading')

    it 'should render highlights when they come before text', ->
      @model.set(highlights: [[4,7],[12,15]])
      @model.set(text: 'foo bar moo mar')
      expect(@subject.$('pre').html()).to.eq('foo <em class="highlight">bar</em> moo <em class="highlight">mar</em>')

    it 'should render the current highlight specially', ->
      @model.set
        highlights: [[4,7],[12,15]]
        text: 'foo bar moo mar'
        highlightsIndex: 1
      expect(@subject.$('pre em:eq(0)')).not.to.have.class('current')
      expect(@subject.$('pre em:eq(1)')).to.have.class('current')

    it 'should wrap when preference is set', ->
      @preferences.set(wrap: true)
      @model.set(text: 'foobar')
      expect(@subject.$('pre')).to.have.class('wrap')

    it 'should not wrap when preference is unset', ->
      @preferences.set(wrap: false)
      @model.set(text: 'foobar')
      expect(@subject.$('pre')).not.to.have.class('wrap')

    it 'should disable wrap after rendering if preference is unset', ->
      @preferences.set(wrap: true)
      @model.set(text: 'foobar')
      @preferences.set(wrap: false)
      expect(@subject.$('pre')).not.to.have.class('wrap')

    it 'should enable wrap after rendering if preference is set', ->
      @preferences.set(wrap: false)
      @model.set(text: 'foobar')
      @preferences.set(wrap: true)
      expect(@subject.$('pre')).to.have.class('wrap')

    it 'should render "only text available" when the preference is to render a document', ->
      @preferences.set(text: false)
      @model.set(text: 'foobar')
      expect(@subject.$('.only-text-available').text()).to.eq('onlyTextAvailable')

    it 'should render "is from OCR" when the document is from OCR', ->
      @model.set(text: 'foobar', isFromOcr: true)
      expect(@subject.$('.is-from-ocr').html()).to.eq('isFromOcr_html')

    it 'should not render "only text available" normally', ->
      @preferences.set(text: true)
      @model.set(text: 'foobar')
      expect(@subject.$('.only-text-available')).not.to.exist
