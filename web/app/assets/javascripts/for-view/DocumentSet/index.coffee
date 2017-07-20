define [ 'jquery' ], ($) ->
  $ ->
    $('#error-list-modal').on('hidden.bs.modal', (-> $(this).removeData('bs.modal')))
