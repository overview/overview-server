define [ 'jquery' ], ($) ->
  $ ->
    $('input[type=password].minlength-password').each ->
      $password = $(this)
      $form = $password.closest('form')

      min_length = parseInt($password.attr('data-minlength-password-min-length'))
      throw "must set data-minlength-password-min-length" if !min_length
      error_message = $password.attr('data-minlength-password-error-too-short') || "too short"

      $error_p = $('<p class="help-block"></p>')
      $error_p.text(error_message)

      is_ok = true

      set_not_ok = () ->
        return if !is_ok
        is_ok = false

        $password.closest('fieldset').addClass('error')
        $password.after($error_p)

      set_ok = () ->
        return if is_ok
        is_ok = true

        $error_p.remove()
        $password.closest('fieldset').removeClass('error')

      refresh = () -> if $password.val().length >= min_length then set_ok() else set_not_ok()
      refresh_if_not_ok = () -> refresh() if !is_ok

      $password.on('change', refresh)
      $password.on('keyup', refresh)
      $password.on('paste', refresh)
      $password.on('cut', refresh)

      $form.on 'submit', (e) ->
        refresh()
        e.preventDefault() if !is_ok
