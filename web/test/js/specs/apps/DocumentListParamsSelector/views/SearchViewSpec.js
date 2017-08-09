'use strict'

const Backbone = require('backbone')
const i18n = require('i18n')
const SearchView = require('apps/DocumentListParamsSelector/views/SearchView')

describe('apps/DocumentListParamsSelector/views/SearchView', function() {
  beforeEach(function() {
    i18n.reset_messages_namespaced('views.DocumentSet.show.SearchView', {
      query_placeholder: 'query_placeholder',
      button: 'button',
      nix: 'nix',
      filter: 'filter',
      'field.title': 'field.title',
      'field.text': 'field.text',
      'field.notes': 'field.notes',
      'field.metadataHeader': 'field.metadataHeader',
      openMetadataSchemaEditor: 'openMetadataSchemaEditor',
    })

    this.documentSet = new Backbone.Model({ metadataSchema: { fields: [ { name: 'foo' }, { name: 'bar' } ] } })
    this.state = {
      documentSet: this.documentSet,
      refineDocumentListParams: sinon.spy(),
    }
    this.globalActions = {
      openMetadataSchemaEditor: sinon.spy(),
    }
    this.model = new Backbone.Model({ q: '' })
    this.subject = new SearchView({
      model: this.model,
      state: this.state,
      globalActions: this.globalActions,
    })
    this.input = this.subject.el.querySelector('input[name=query]')

    document.body.appendChild(this.subject.el)
  })

  afterEach(function() {
    this.subject.remove()
  })

  it('should render an empty value', function() {
    expect(this.input.value).to.eq('')
  })

  it('should change when q changes', function() {
    this.model.set({ q: 'foo' })
    expect(this.input.value).to.eq('foo')
  })

  it('should add refine empty by text', function() {
    this.input.value = ''
    this.subject.$('[data-field=text]').click()
    expect(this.input.value).to.eq('text:')
  })

  it('should focus after refining', function() {
    this.subject.$('[data-field=text]').click()
    expect(document.activeElement).to.eq(this.input)
  })

  it('should refine non-empty by adding AND', function() {
    this.input.value = 'foo'
    this.subject.$('[data-field=text]').click()
    expect(this.input.value).to.eq('foo AND text:')
  })

  it('should not add an extra space before AND', function() {
    this.input.value = 'foo '
    this.subject.$('[data-field=text]').click()
    expect(this.input.value).to.eq('foo AND text:')
  })

  it('should refine by title', function() {
    this.input.value = 'foo'
    this.subject.$('[data-field=title]').click()
    expect(this.input.value).to.eq('foo AND title:')
  })

  it('should change refining from text to title when no content has been written and there is an AND', function() {
    this.input.value = 'foo AND text:'
    this.subject.$('[data-field=title]').click()
    expect(this.input.value).to.eq('foo AND title:')
  })

  it('should change refining from text to title when no content has been written and there is no AND', function() {
    this.input.value = 'text:'
    this.subject.$('[data-field=title]').click()
    expect(this.input.value).to.eq('title:')
  })

  it('should change refining from a field with escapes', function() {
    this.input.value = 'x AND "foo\\"":'
    this.subject.$('[data-field=title]').click()
    expect(this.input.value).to.eq('x AND title:')
  })

  it('should add new refine when content has been written', function() {
    this.input.value = 'foo:b'
    this.subject.$('[data-field=title]').click()
    expect(this.input.value).to.eq('foo:b AND title:')
  })

  it('should show a metadata field', function() {
    this.subject.$('[data-field=foo]').click()
    expect(this.input.value).to.eq('foo:')
  })

  it('should watch documentSet for changes in metadata fields', function() {
    this.documentSet.set({ metadataSchema: { fields: [ { name: 'baz' } ] } })
    expect(this.subject.$('[data-field=foo]')).not.to.exist
    expect(this.subject.$('[data-field=bar]')).not.to.exist
    expect(this.subject.$('[data-field=baz]')).to.exist
  })

  it('should quote metadata field if needed', function() {
    this.documentSet.set({ metadataSchema: { fields: [
      { name: 'foo bar' },
      { name: '<>' },
      { name: 'who\'s there' },
      { name: '"I am"' },
      { name: 'text' },
      { name: 'title' },
      { name: 'notes' },
    ] } })

    const fieldNames = Array.prototype.slice.apply(this.subject.el.querySelectorAll('a[data-field]'))
      .map(el => el.getAttribute('data-field'))

    const expected = [
      'text',
      'title',
      'notes',
      '"foo bar"',
      '"<>"',
      '"who\'s there"',
      '"\\"I am\\""',
      '"text"',
      '"title"',
      '"notes"',
    ]

    expected.forEach((expectFieldName, i) => {
      expect(fieldNames[i]).to.eq(expectFieldName)
    })
  })

  it('should remove refresh "changing" status after refine', function() {
    this.subject.$('[data-field=title]').click()
    expect(this.subject.el.classList.contains('changing')).to.eq(true)
  })

  it('should open the metadata schema editor', function() {
    this.subject.$('a.open-metadata-schema-editor').click()
    expect(this.globalActions.openMetadataSchemaEditor).to.have.been.called
  })
})
