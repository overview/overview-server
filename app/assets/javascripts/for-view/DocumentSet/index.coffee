define [ 'jquery' ], ($) ->
  $ ->
    $('#error-list-modal').on('hidden', (() -> $(this).removeData('modal')))
