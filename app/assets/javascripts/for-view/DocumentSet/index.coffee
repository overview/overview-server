define [ 'jquery' ], ($) ->
  $ ->
    $('#error-list-modal, #export-modal').on('hidden', (() -> $(this).removeData('modal')))
