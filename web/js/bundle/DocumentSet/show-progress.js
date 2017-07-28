require('for-view/DocumentSet/cancel-import-job')
require('for-view/DocumentSet/delete-document-set')
require('for-view/DocumentSet/share-document-set')
require('for-view/DocumentSet/watch-import-jobs')
require('for-view/DocumentSet/show-progress')
require('elements/logged-out-modal')
require('elements/twitter-bootstrap')
// Expose jQuery, so integration tests can detect loading is done
module.exports = require('jquery')
