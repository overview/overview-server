define [ 'jquery' ], ($) ->
  $ ->
    $('#error-list-modal, #export-modal').on('hidden.bs.modal', (-> $(this).removeData('bs.modal')))
