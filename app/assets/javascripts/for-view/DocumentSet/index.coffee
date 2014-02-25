define [ 'jquery', 'elements/jquery-time_display' ], ($) ->
  $ ->
    $('#error-list-modal, #export-modal').on('hidden', (() -> $(this).removeData('modal')))
    $('time').time_display()
