require [
  'jquery'
], ($) ->
  $ ->
    $(document).on 'click', 'a.delete-document-set', (e) ->
      e.preventDefault()
      $a = $(e.currentTarget)
      documentSetId = $a.attr('data-document-set-id')
      text = $a.attr('data-confirm')

      if window.confirm(text)
        window.location = "/documentsets/#{documentSetId}?X-HTTP-Method-Override=DELETE"
