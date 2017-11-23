const Backbone = require('backbone')
const ViewFilterView = require('./ViewFilterView')

module.exports = class FiltersView extends Backbone.View {
  initialize(options) {
    if (!this.collection) throw new Error('Must pass options.model, a Collection of "view" models')

    if (!options.state) throw new Error('Must pass options.state, a State')
    this.state = options.state

    this.listenTo(this.collection, 'change update', this.render)
    this.cachedFilters = {} // conceptually, "what we last rendered"
    this.render()
  }

  render() {
    for (const key of Object.keys(this.cachedFilters)) {
      this.cachedFilters[key].markedForDeletion = true
    }

    this.collection.forEach(view => {
      if (!view.get('filter')) return // Do not render views unless they have filters

      let id = view.id
      if (!id) throw new Error("ViewFilter must have an 'id' property")
      if (id.startsWith('view-')) id = id.slice(5) // ugly hack upon ugly hack. Views should not have types in their IDs.

      const existingFilterView = this.cachedFilters[id]
      if (existingFilterView) {
        existingFilterView.setChoices(view.get('filter').choices)
        existingFilterView.markedForDeletion = false
      } else {
        const newFilterView = new ViewFilterView({ id: id, state: this.state, viewFilter: view.get('filter') })
        this.el.appendChild(newFilterView.el)
        newFilterView.attachEventListeners()
        this.cachedFilters[id] = newFilterView
      }
    })

    for (const key of Object.keys(this.cachedFilters)) {
      const filter = this.cachedFilters[key]
      if (filter.markedForDeletion) {
        filter.remove()
        delete this.cachedFilters[key]
      }
    }
  }
}
