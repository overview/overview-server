app = require('app')

exports = require.make_export_object('globals')
exports.document_store = new app.models.DocumentStore()
exports.router = new app.models.Router()
