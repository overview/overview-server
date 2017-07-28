define [
  'jquery'
  'apps/DocumentSetUsers/app'
], ($, App) ->
  $ ->
    $app = $('#app')
    emails = JSON.parse($app.attr('data-emails'))
    isPublic = JSON.parse($app.attr('data-public'))
    admin = JSON.parse($app.attr('data-admin'))
    documentSetId = /\/documentsets\/(\d+)/.exec(window.location)[1]

    app = new App
      el: $app
      emails: emails
      isPublic: isPublic
      documentSetId: documentSetId
      isAdmin: admin
      csrfToken: window.parent.csrfToken
