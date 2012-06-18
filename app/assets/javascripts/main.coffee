app = require('app')

state = new app.models.State()
state.set('document_store', new app.models.DocumentStore())
state.set('tree', new app.models.PartialTree())

console.log("State", state)
