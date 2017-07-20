define [
  'backbone'
  'apps/DocumentMetadata/views/JsonView'
  'i18n'
], (Backbone, JsonView, i18n) ->
  describe 'apps/DocumentMetadata/views/JsonView', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.DocumentSet.show.DocumentMetadata.JsonView',
        help_html: 'help_html'
        delete: 'delete'
        confirmDelete: 'confirmDelete,{0}'

      @sandbox = sinon.sandbox.create()
      @model = new Backbone.Model
        fields: [ 'field1', 'field2', 'field3' ]
        json: { field1: 'value1', field2: 'value2', field3: 'value3' }
      @subject = new JsonView(model: @model)

    afterEach ->
      @subject.stopListening()
      @sandbox.restore()

    it 'should be a form', ->
      expect(@subject.$el).to.match('form')

    it 'should show help when empty', ->
      @model.set(fields: [])
      expect(@subject.$('.help').html()).to.eq('help_html')

    it 'should display fields in the order specified in metadataFields', ->
      expect(@subject.$('label:eq(0)')).to.have.text('field1')
      expect(@subject.$('label:eq(1)')).to.have.text('field2')
      expect(@subject.$('label:eq(2)')).to.have.text('field3')

    it 'should assign fields their default values in HTML', ->
      expect(@subject.$('input:eq(0)')).to.have.attr('value', 'value1')
      expect(@subject.$('input:eq(1)')).to.have.attr('value', 'value2')
      expect(@subject.$('input:eq(2)')).to.have.attr('value', 'value3')

    it 'should reorder fields', ->
      @model.set(fields: [ 'field3', 'field1', 'field2' ])
      expect(@subject.$('label:eq(0)')).to.have.text('field3')
      expect(@subject.$('label:eq(1)')).to.have.text('field1')
      expect(@subject.$('label:eq(2)')).to.have.text('field2')

    it 'should not overwrite HTML when reordering fields', ->
      @subject.$('input:eq(0)').val('new value')
      @model.set(fields: [ 'field3', 'field1', 'field2' ])
      expect(@subject.$('input:eq(1)')).to.have.value('new value')

    it 'should add a new field to the end of the list', ->
      @model.set(fields: [ 'field1', 'field2', 'field3', 'foo' ])
      expect(@subject.$('label:eq(3)')).to.have.text('foo')

    it 'should give a new field a default value', ->
      @model.set(fields: [ 'field1', 'field2', 'field3', 'foo' ])
      expect(@subject.$('input:eq(3)')).to.have.value('')

    it 'should save on change', ->
      @subject.$('input:eq(0)').val('newValue1')
      @subject.$('input:eq(1)').val('newValue2').change()
      expect(@model.get('json')).to.deep.eq
        field1: 'newValue1'
        field2: 'newValue2'
        field3: 'value3'

    it 'should not delete a metadata field when confirm is false', ->
      @sandbox.stub(window, 'confirm').returns(false)
      @subject.$('button.delete:eq(0)').click()
      expect(@model.get('fields')).to.deep.eq([ 'field1', 'field2', 'field3' ])

    it 'should confirm when deleting a metadata field', ->
      @sandbox.stub(window, 'confirm').returns(false)
      @subject.$('button.delete:eq(0)').click()
      expect(window.confirm).to.have.been.calledWith('confirmDelete,field1')

    it 'should delete a metadata field', ->
      @sandbox.stub(window, 'confirm').returns(true)
      @subject.$('button.delete:eq(0)').click()
      expect(@model.get('fields')).to.deep.eq([ 'field2', 'field3' ])
      # And as a consequence...
      expect(@subject.$('label,input')).to.have.length(4)
      expect(@subject.$('label:eq(0)')).to.have.text('field2')
      expect(@subject.$('label:eq(1)')).to.have.text('field3')
      expect(@subject.$('input:eq(0)')).to.have.attr('value', 'value2')
      expect(@subject.$('input:eq(1)')).to.have.attr('value', 'value3')
