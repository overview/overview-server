$ ->
  $('#error-list-modal').on('hidden', (() -> $(this).removeData('modal')))

  $('ul.document-sets').on 'change click', 'form.update input[type=checkbox]', (e) ->
    $checkbox = $(e.currentTarget)
    $checkbox.closest('form').submit()

  $('ul.document-sets').on 'submit', 'form.update', (e) ->
    $form = $(e.currentTarget)
    data = $form.serialize()
    old_data = $form.data('last-data')
    if data != old_data
      $form.data('last-data', data)
      $.ajax({
        type: 'PUT'
        url: $form.attr('action')
        data: data
      })
    e.preventDefault()
