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
        $.ajax({
          url: "/documentsets/#{documentSetId}"
          type: 'DELETE'
          data: {
            csrfToken: window.csrfToken
          }
          success: () => window.location = '/documentsets'
          error: (xhr) => console.warn("ERROR during delete", xhr)
        })
