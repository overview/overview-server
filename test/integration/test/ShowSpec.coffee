asUserWithDocumentSet = require('../support/asUserWithDocumentSet-new')

# describe 'Show', ->
#   asUserWithDocumentSet 'Show/basic.csv', ->
#     before ->
#       @browser
#         .loadShortcuts('documentSet')

#     describe 'after renaming a document title', ->
#       before ->
#         @browser.shortcuts.documentSet.renameDocument('Facebook 1', 'Facebook Z')

#       after ->
#         @browser.shortcuts.documentSet.renameDocument('Facebook Z', 'Facebook 1')

#       it 'should search for the edited title', ->
#         @browser
#           .shortcuts.documentSet.search('Facebook Z')
#           .shortcuts.documentSet.waitUntilDocumentListLoaded()
#           .getText(id: 'document-list-title')
#             .then((text) => expect(text).to.match(/Found one document/))
