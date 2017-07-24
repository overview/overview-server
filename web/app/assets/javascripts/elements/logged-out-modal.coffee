define ['jquery', 'i18n', 'underscore', 'bootstrap-modal'], ($, i18n, _) ->
  $(document).ajaxError((event, jqxhr) ->
    console.log(jqxhr.status)
    if jqxhr.status == 400
      body = try
        JSON.parse(jqxhr.responseText)
      catch
        {}

      if body.code == 'unauthenticated'
        t = i18n.namespaced('authentication.LoggedOutModal')

        modal = _.template("""
          <form method="get" action="#" id="logged-out-modal" class="modal" role="dialog">
            <div class="modal-dialog">
              <div class="modal-content">
                <div class="modal-header">
                  <h4 class="modal-title"><%- t('title') %></h4>
                </div>
                <div class="modal-body">
                  <p><%- t('body') %></p>
                </div>
                <div class="modal-footer">
                  <!-- below, onclick = yuck, but a very convenient kind of yuck. -->
                  <button class="btn btn-primary" onclick="document.location.reload(true); return false;"><%- t('button') %></button>
                </div>
              </div>
            </div>
          </form>
        """)(t: t)

        $modal = $(modal)
        $('body').append($modal)

        # hide any bootstrap modals that may be active, b/c only
        # one can be shown at a time, before activating the new one.
        # The 'backdrop' and 'keyboard' options make this modal uncloseable,
        # such that the user's only option is going back to the login screen.
        $('.modal').modal('hide');
        $modal.modal(keyboad: false, backdrop: 'static')
  )
