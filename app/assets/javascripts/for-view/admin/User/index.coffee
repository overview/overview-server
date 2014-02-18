define [ 'jquery', 'apps/UserAdmin/App' ], ($, App) ->
  $ ->
    $el = $('.app')
    adminEmail = $el.attr('data-admin-email')

    throw "Must set data-admin-email" if !adminEmail
    new App(el: $el.get(0), adminEmail: adminEmail)
