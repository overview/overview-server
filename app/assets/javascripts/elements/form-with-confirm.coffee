$ ->
  $('body').on 'submit', 'form[data-confirm]', (e) ->
    message = $(this).attr('data-confirm')
    e.preventDefault() if !window.confirm(message)
