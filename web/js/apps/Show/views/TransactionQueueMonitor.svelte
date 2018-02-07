<div class="transaction-queue-monitor">
  {{#if isCommunicating}}
    <div class="transaction-queue-communicating"><i title={{t('communicating')}} class="icon icon-spinner icon-pulse"></i></div>
  {{/if}}
  {{#if error}}
    <div class="modal fade in" style="display:block;">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h4 class="modal-title">{{t('error.title')}}</h4>
          </div>
          <div class="modal-body">
            <p>{{t('error.description')}}</p>
          </div>
          <div class="modal-footer">
            <button on:click="reload()" type="button" class="btn btn-primary reload">{{t('reload')}}</button>
          </div>
        </div>
      </div>
    </div>
  {{/if}}
  {{#if networkError}}
    <div class="modal fade in" style="display:block;">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h4 class="modal-title">{{t('networkError.title')}}</h4>
          </div>
          <div class="modal-body">
            <p>{{t('networkError.description')}}</p>
          </div>
          <div class="modal-footer">
            <button on:click="reload()" type="button" class="btn btn-warning reload">{{t('reload')}}</button>
            <button on:click="retry()" type="button" class="btn btn-primary retry">{{t('retry')}}</button>
          </div>
        </div>
      </div>
    </div>
  {{/if}}
</div>

<script>
  import i18n from 'i18n'

  export default {
    data() {
      return {
        nAjaxIncomplete: 0,
        error: null,
        networkError: null,
        t: i18n.namespaced('views.DocumentSet.show.TransactionQueueMonitor'),
      }
    },

    computed: {
      isCommunicating: (nAjaxIncomplete) => nAjaxIncomplete > 0,
    },

    methods: {
      reload() {
        this.fire('reload')
      },

      retry() {
        this.fire('retry')
      },
    },
  }
</script>
