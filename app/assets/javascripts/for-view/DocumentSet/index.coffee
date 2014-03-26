define [ 'jquery', 'elements/jquery-time_display' ], ($) ->
  $ ->
    $('#error-list-modal, #export-modal').on('hidden.bs.modal', (-> $(this).removeData('modal')))
    $('time').time_display()
