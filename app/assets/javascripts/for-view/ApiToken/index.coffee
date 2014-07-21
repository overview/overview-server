define [
  'jquery'
  'apps/ApiTokens/app'
], ($, ApiTokensApp) ->
  $ ->
    $el = $('#api-tokens-app')
    documentSetId = parseInt($el.attr('data-document-set-id'), 10)

    new ApiTokensApp
      el: $el[0]
      documentSetId: documentSetId
