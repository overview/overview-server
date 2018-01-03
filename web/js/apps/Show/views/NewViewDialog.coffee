define [
  'underscore'
  'jquery'
  'i18n'
  'bootstrap-modal'
], (_, $, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.NewViewDialog')

  class NewViewDialog
    constructor: (options) ->
      throw 'Must pass options.success, a function that accepts a { title: ..., url: ... }' if !options?.success

      @_success = options.success
      @$container = $(options.container ? 'body')

      @secure = {}
      @statuses = {}
      @xhrs = {}
      @xhr = null # XHR for currently-entered URL

      html = _.template("""
        <form method="get" action="#" id="new-view-dialog" class="modal" role="dialog" novalidate>
          <!-- "novalidate" because we call reportValidity() manually on submit -->
          <div class="modal-dialog">
            <div class="modal-content">
              <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal">×</button>
                <h4 class="modal-title"><%- t('title') %></h4>
              </div>
              <div class="modal-body">
                <div class="form-group">
                  <label for="new-view-dialog-title"><%- t('title.label') %></label>
                  <input
                    id="new-view-dialog-title"
                    name="title"
                    placeholder="<%- t('title.placeholder') %>"
                    class="form-control"
                    required
                    />
                </div>
                <div class="form-group">
                  <label for="new-view-dialog-url"><%- t('url.label') %></label>
                  <input
                    id="new-view-dialog-url"
                    name="url"
                    type="url"
                    pattern="(https?:)?//.*"
                    placeholder="<%- t('url.placeholder') %>"
                    class="form-control"
                    required
                    />
                  <div class="state">
                    <div class="checking"><%- t('url.checking') %></div>
                    <div class="ok"><%- t('url.ok') %></div>
                    <div class="unavailable">
                      <span class="message"></span>
                      <a href="#" class="retry"><%- t('url.unavailable.retry') %></a>
                    </div>
                    <div class="insecure">
                      <%= t('url.insecure_html') %>
                      <a href="#" class="dismiss"><%- t('url.insecure.dismiss') %></a>
                    </div>
                  </div>
                </div>
                <div class="form-group">
                  <label for="new-view-dialog-server-url-from-plugin"><%- t('serverUrlFromPlugin.label') %></label>
                  <input
                    id="new-view-dialog-server-url-from-plugin"
                    name="serverUrlFromPlugin"
                    type="url"
                    pattern="https?://.*"
                    class="form-control"
                    />
                  <p class="help-block"><%- t('serverUrlFromPlugin.help') %></p>
                </div>
              </div>
              <div class="modal-footer">
                <input type="reset" class="btn" data-dismiss="modal" value="<%- t('cancel') %>" />
                <input type="submit" class="btn btn-primary" value="<%- t('submit') %>" />
              </div>
            </div>
          </div>
        </form>
      """)(t: t)
      @$el = $(html)
      @$container.append(@$el)

      @$el.on('click', 'input[type=reset]', @onReset.bind(@))
      @$el.on('click', 'input[type=submit]', @onSubmit.bind(@))
      @$el.on('click', 'a.retry', @onRetry.bind(@))
      @$el.on('click', 'a.dismiss', @onDismiss.bind(@))

      # [adamhooper, 2018-01-02] we now call onChangeUrl() only during submit:
      # otherwise, the change event will alter HTML, moving the submit button
      # mid-click and altering the user's target.
      #@$el.on('change', 'input[name=url]', @onChangeUrl.bind(@))

      $state = @$el.find('.state')
      @$els =
        title: @$el.find('[name=title]')
        url: @$el.find('[name=url]')
        serverUrlFromPlugin: @$el.find('[name=serverUrlFromPlugin]')
        submit: @$el.find('[type=submit]')

        state:
          all: $state.children()
          checking: $state.children('.checking')
          ok: $state.children('.ok')
          unavailable: $state.children('.unavailable')
          insecure: $state.children('.insecure')
          invalid: $state.children('.invalid')

      @refreshState()
      if @url?
        @setUrlState(@url, 'ok')

      @$el.modal('show')
      @$el.find('input:eq(0)').focus().select()
      @$el.one 'shown.bs.modal', =>
        @$el.find('input:eq(0)').focus().select()
      @$el.on('hidden.bs.modal', => @remove())

    attrs: ->
      title: @$els.title.val()
      url: @$els.url.val()
      serverUrlFromPlugin: @$els.serverUrlFromPlugin.val()

    refreshState: ->
      @state = ''
      @$els.state.all.hide()

    remove: ->
      @$el.modal('hide')
      @$el.remove()
      @$el.off()
      $(document).off('.bs.modal')

    onReset: -> @remove()

    onSubmit: (e) ->
      e.preventDefault()

      # Only validate URL on submit. That helps during editing: if we edit an
      # invalid URL and then try to click "Create visualization", the button
      # will move as our validation code alters HTML. Better to alter the HTML
      # _after_ clicking the button
      @onChangeUrl()

      # We get here always on click, because we set "novalidate" on the HTML
      # element. So now we'll call reportValidity() and then submit the data
      # via AJAX on success.
      #
      # Why "novalidate"? Because URL validation is asynchronous. That means
      # this function would never be called when the user clicks "submit"
      # while checking the URL. But we _want_ this code to be called when the
      # user hits "submit" while checking the URL: it means to us, "wait until
      # validation succeeds, then re-click this button"
      #
      # [adamhooper, 2018-01-02] novalidate+reportValidity() should emulate HTML
      # perfectly, from my reading of
      # https://html.spec.whatwg.org/multipage/form-control-infrastructure.html#statically-validate-the-constraints

      if @state == 'checking'
        throw new Error('Expected @xhr to be set') if !@xhr?
        @xhr.addEventListener('loadend', => @onSubmit(e))
        return

      if e.target.form.reportValidity()
        return if @submitted # Paranoid: avoid double-submit
        @submitted = true

        @_success(@attrs())
        @remove()

    onRetry: (e) ->
      e.preventDefault()
      url = @attrs().url
      delete @statuses[url]
      @checkUrl(url)

    onDismiss: (e) ->
      e.preventDefault()
      url = @attrs().url
      @secure[url] = true
      @checkUrl(url)

    onChangeUrl: ->
      $url = @$els.url
      url = $url.val()
      $url[0].setCustomValidity('') # so we can call checkValidity()
      if $url[0].checkValidity()
        @checkUrl(url)
      else
        @setUrlState(url, 'invalid')

    setUrlState: (url, state, statusCode) ->
      if url == @attrs().url
        @state = state
        @$els.state.all.hide()
        @$els.state[state].show()

        if state == 'unavailable'
          @$els.state.unavailable.children('.message')
            .html(t('url.unavailable_html', "#{url}/metadata", statusCode))

        # We setCustomValidity, but that doesn't disable the submit button
        # because we use novalidate. That lets the user click submit while we're
        # checking the URL.
        if @state == 'ok'
          @$els.url[0].setCustomValidity('')
        else
          @$els.url[0].setCustomValidity(t('url.readHtmlMessage'))

    # Checks if the URL is okay, synchronously. If not, runs stuff in the
    # background and calls itself in the background.
    #
    # Effectively: calls setUrlState() once synchronously and more times
    # asynchronously.
    checkUrl: (url) ->
      if url == @url
        @setUrlState(url, 'ok')
      else if !@checkSecure(url)
        @setUrlState(url, 'insecure')
      else
        status = @checkStatus(url)
        if !status?
          @setUrlState(url, 'checking')
        else if status >= 200 && status < 300
          @setUrlState(url, 'ok')
        else
          @setUrlState(url, 'unavailable', status)

    checkSecure: (url) ->
      if /^http:\/\//.test(url)
        @secure[url] || false
      else
        true

    checkStatus: (url) ->
      if url of @statuses
        @statuses[url]
      else
        if url not of @xhrs
          # Avoid $.ajax(), which uses TransactionQueue. We don't want _any_
          # of TransactionQueue's features.
          @xhr = @xhrs[url] = new XMLHttpRequest()
          @xhr.timeout = 30000
          @xhr.addEventListener('loadend', =>
            @statuses[url] = @xhr.status
            delete @xhrs[url]
            @xhr = null if @xhr == @xhrs[url]
            @checkUrl(url)
          )
          @xhr.open('GET', "#{url}/metadata")
          @xhr.send()
        null
