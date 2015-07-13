define [
  'backbone'
  'apps/DocumentMetadata/views/JsonView'
  'i18n'
], (Backbone, JsonView, i18n) ->
  describe 'apps/DocumentMetadata/views/JsonView', ->
    beforeEach ->
      @documentSet = new Backbone.Model(metadataFields: [ 'field1', 'field2', 'field3' ])
      @document = new Backbone.Model(url: 'foo', metadata: { field1: 'value1', field2: 'value2', field3: 'value3' })
      @document.save = sinon.spy()
      @subject = new JsonView(documentSet: @documentSet, document: @document)

    afterEach ->
      @subject.stopListening()

    it 'should be a form', ->
      expect(@subject.$el).to.match('form')

    it 'should show help when empty', ->
      i18n.reset_messages('views.DocumentSet.show.DocumentMetadata.JsonView.help_html': 'help_html')
      @documentSet.set(metadataFields: [])
      expect(@subject.$('.help').html()).to.eq('help_html')

    it 'should display fields in the order specified in metadataFields', ->
      expect(@subject.$('dt:eq(0)')).to.contain('field1')
      expect(@subject.$('dt:eq(1)')).to.contain('field2')
      expect(@subject.$('dt:eq(2)')).to.contain('field3')

    it 'should assign fields their default values in HTML', ->
      expect(@subject.$('input:eq(0)')).to.have.attr('value', 'value1')
      expect(@subject.$('input:eq(1)')).to.have.attr('value', 'value2')
      expect(@subject.$('input:eq(2)')).to.have.attr('value', 'value3')

    it 'should reorder fields', ->
      @documentSet.set(metadataFields: [ 'field3', 'field1', 'field2' ])
      expect(@subject.$('dt:eq(0)')).to.contain('field3')
      expect(@subject.$('dt:eq(1)')).to.contain('field1')
      expect(@subject.$('dt:eq(2)')).to.contain('field2')

    it 'should not overwrite HTML when reordering fields', ->
      @subject.$('input:eq(0)').val('new value')
      @documentSet.set(metadataFields: [ 'field3', 'field1', 'field2' ])
      expect(@subject.$('input:eq(1)')).to.have.value('new value')

    it 'should add a new field to the end of the list', ->
      @documentSet.set(metadataFields: [ 'field1', 'field2', 'field3', 'foo' ])
      expect(@subject.$('dt:eq(3)')).to.contain('foo')

    it 'should give a new field a default value', ->
      @documentSet.set(metadataFields: [ 'field1', 'field2', 'field3', 'foo' ])
      expect(@subject.$('input:eq(3)')).to.have.value('')

    it 'should save on change', ->
      @subject.$('input:eq(0)').val('newValue1')
      @subject.$('input:eq(1)').val('newValue2').change()
      expect(@document.save).to.have.been.calledWith({ metadata: {
        field1: 'newValue1',
        field2: 'newValue2',
        field3: 'value3'
      } }, patch: true)
