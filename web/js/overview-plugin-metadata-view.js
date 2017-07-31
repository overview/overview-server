'use strict'

const escapeHtml = require('escape-html')

function buildFieldValueHtml(field, documentValue) {
  const valueHtml = escapeHtml(documentValue)

  switch (field.display) {
    case 'Div': return `<div class="field-value">${valueHtml}</div>`
    case 'Pre': return `<pre class="field-value">${valueHtml}</pre>`
    case 'TextInput':
    default:
      return `<input type="text" class="field-value" value="${valueHtml}">`
  }
}

function buildFieldHtml(field, documentValue) {
  return [
    `<tr data-field-name="${escapeHtml(field.name)}">`,
    `<th><div class="field-name">${escapeHtml(field.name)}</div></th>`,
    `<td data-display="${field.display}">${buildFieldValueHtml(field, documentValue)}</td>`,
    '</tr>',
  ].join('')
}

function ignoreSubmit(ev) {
  ev.preventDefault()
}

module.exports = class MetadataView {
  constructor(el, api) {
    this.el = el
    this.api = api

    this.metadataSchema = { "version": 1, "fields": [] }
    this.metadata = {}
    this.documentId = null

    api.onDocumentSetChanged(documentSet => this.onDocumentSetChanged(documentSet))
    api.onDocumentChanged(document => this.onDocumentChanged(document))
    api.requestDocumentSet()
    api.requestDocument()
    el.addEventListener('submit', ignoreSubmit)
    el.addEventListener('change', () => this.saveNewMetadata())
  }

  buildHtml() {
    return [
      '<p class="if-no-document">Open a document to edit its fields</p>',
      '<table>',
      this.metadataSchema.fields.map(field => buildFieldHtml(field, this.metadata && this.metadata[field.name] || '')).join(''),
      '</table>',
      '<p class="add-fields"><a href="#" class="add-fields">Organize fields\u2026</a></p>',
    ].join('')
  }

  updateTrValue(tr) {
    const fieldName = tr.getAttribute('data-field-name')
    const field = this.metadataSchema.fields.find(field => field.name === fieldName)
    if (!field) {
      throw new Error(`Could not find field named ${fieldName} in metadataSchema`)
    }
    const td = tr.querySelector('td')
    const value = this.metadata && this.metadata[fieldName] || ''

    switch (field.display) {
      case 'Div':
      case 'Pre':
        td.innerHTML = buildFieldValueHtml(field, value)
      case 'TextInput':
      default:
        const input = tr.querySelector('input')
        if (input) {
          input.value = value
        }
    }
  }

  updateHtml() {
    this.el.classList.toggle('no-document', !this.documentId)
    const trs = Array.prototype.slice.apply(this.el.querySelectorAll('tr[data-field-name]'))
    trs.forEach(tr => this.updateTrValue(tr))
  }

  onDocumentSetChanged(documentSet) {
    this.metadataSchema = documentSet.metadataSchema
    this.el.innerHTML = this.buildHtml()
    this.el.querySelector('a.add-fields').addEventListener('click', ev => {
      ev.preventDefault()
      this.api.openMetadataSchemaEditor()
    })
  }

  onDocumentChanged(document) {
    this.documentId = document ? document.id : null
    this.metadata = document ? document.metadata : {}
    this.updateHtml()
  }

  saveNewMetadata() {
    const newValues = {}
    const trs = Array.prototype.slice.apply(this.el.querySelectorAll('tr[data-field-name]'))
    for (const tr of trs) {
      const fieldName = tr.getAttribute('data-field-name')
      const input = tr.querySelector('input')
      if (input) {
        newValues[fieldName] = input.value
      }
    }

    // We'll only update the parts of the metadata JSON that the user sees. Other
    // JSON values that aren't part of the schema won't be changed.
    Object.assign(this.metadata, newValues)
    this.api.saveDocumentMetadata(this.documentId, this.metadata)
  }
}
