define [ 'jquery' ], ($) ->
  $ ->
    $document_sets = $('.document-sets')
    if !$document_sets.length
      # The user has no document sets. Let's help import one.
      $('button.toggle-import').click()
