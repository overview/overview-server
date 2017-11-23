'use strict'

const FilterView = require('./FilterView')

class ViewFilterView {
  constructor(options) {
    if (!options.id) throw new Error('Must set options.id, a Number')
    if (!options.viewFilter) throw new Error('Must set options.viewFilter, an Object')
    if (!options.state) throw new Error('Must set options.state, a State')

    this.id = options.id
    this.state = options.state

    this.filterView = new FilterView({
      renderOptions: options.viewFilter.renderOptions,
      choices: options.viewFilter.choices,
      selection: { ids: [], operation: 'any' },
      onSelect: selection => this.onUserSelectedSomething(selection),
    })

    this.stateListener = (_, documentList) => {
      this.onDocumentListParamsChanged(documentList ? documentList.params : null)
    }

    this.state.on('change:documentList', this.stateListener)

    this.el = this.filterView.el
  }

  setChoices(...args) { this.filterView.setChoices(...args) }
  attachEventListeners(...args) { this.filterView.attachEventListeners(...args) }

  remove() {
    this.state.off('change:documentList', this.stateListener)
    this.el.parentNode.removeChild(this.el)
  }

  onUserSelectedSomething(selection) {
    const filters = {}
    filters[String(this.id)] = selection
    this.state.refineDocumentListParams({ filters: filters })
  }

  onDocumentListParamsChanged(params) {
    const selection = (params && params.filters && params.filters[String(this.id)]) || { ids: [], operation: 'any' }
    this.filterView.setSelection(selection)
  }
}

module.exports = ViewFilterView
