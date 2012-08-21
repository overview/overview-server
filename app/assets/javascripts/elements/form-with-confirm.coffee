$ ->
  $('form[data-confirm]').each ->
    message = $(this).attr('data-confirm')

    $(this).submit (e) ->
      e.preventDefault() if !window.confirm(message)
