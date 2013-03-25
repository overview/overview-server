define [ 'jquery' ], ($) ->
  $ ->
    $('input[type=password].confirm-password').each ->
      $confirm = $(this)
      $form = $confirm.closest('form')
      $password = $form.find('input[type=password]:not(.confirm-password)')

      error_message = $confirm.attr('data-confirm-password-error-does-not-match') || "does not match"
      $error_p = $('<p class="help-block"></p>')
      $error_p.text(error_message)

      is_ok = true

      set_not_ok = () ->
        return if !is_ok
        is_ok = false

        $confirm.closest('fieldset').addClass('error')
        $confirm.after($error_p)

      set_ok = () ->
        return if is_ok
        is_ok = true

        $error_p.remove()
        $confirm.closest('fieldset').removeClass('error')

      confirmation_matches = () ->
        !$confirm.val() || !$password.val() || $confirm.val() == $password.val()

      refresh = () -> if confirmation_matches() then set_ok() else set_not_ok()
      refresh_if_not_ok = () -> refresh() if !is_ok

      $fields = $confirm.add($password)
      $fields.on('change', refresh)
      $fields.on('keyup', refresh)
      $fields.on('paste', refresh)
      $fields.on('cut', refresh)

      $form.on 'submit', (e) ->
        refresh()
        e.preventDefault() if !is_ok || !$confirm.val() || !$password.val()
