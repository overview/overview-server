/* ================================================
 * Make use of Twitter Bootstrap's modal more monkey-friendly
 * Licensed under The MIT License.
 * ================================================ */
var BootstrapDialog = window.BootstrapDialog = null;
!function($){
	"use strict";
	
	BootstrapDialog = function(options){
		this.defaultOptions = {
			'title'				:	null,
			'content'			:	null,
			'draggableHandles'	:	'',
			'autoDestroy'		:	true,
			'buttons'			:	[],
			'ready'				:	null,
			// Bootstrap's options
			'backdrop'			:	'static'
		};
		this.dataHolder = {};
		this.buttons = {};
		this.init(options);
	};
	
	BootstrapDialog.prototype = {
		constructor: BootstrapDialog,
		init: function(options){
			this.options = $.extend(this.defaultOptions, options, true);
		},
		initDialog: function(){
			this.setDialog(this.createDialog());
			this.setHeader(this.createHeader());
			this.setBody(this.createBody());
			this.setFooter(this.createFooter());
			this.getDialog().append(this.getHeader()).append(this.getBody()).append(this.getFooter());
			if (this.options.autoDestroy) {
				this.getDialog().on('hidden', function(event){
					$(this).remove();
				});
			}
		},
		initDraggable: function(){
			var handles = this.options.draggableHandles.split(',');
			var handlesArray = [];
			for(var i=0; i<handles.length; i++){
				var handle = $.trim(handles[i]);
				if (handle != '') {
					handlesArray.push('.modal-' + handle);
				}
			}
			if (handlesArray.length > 0) {
				this.getDialog().draggable({
					handle: handlesArray.join(',')
				});
			}
		},
		initButtons: function(){
			var $footer = this.getFooter();
			var btns = this.getButtons();
			for(var i=0; i<btns.length; i++){
				var btn = btns[i];
				var $btn = $('<button class="btn">Button</button>');
				if (btn.id){
					this.buttons[btn.id] = $btn;
				}
				if (btn.label) {
					$btn.text(btn.label);
				}
				if (btn.cssClass) {
					$btn.addClass(btn.cssClass);
				}
				$btn.click({btn: btn, $btn: $btn, dialog: this}, function(event){
					if(event.data.btn.onclick){
						event.data.btn.onclick.call(event.target, event.data.dialog);
					}
				});
				$footer.append($btn);
			}
		},
		open: function(){
			this.initDialog();
			this.initDraggable();
			this.initButtons();
			this.ready();
			this.getDialog().modal('show');
			
			return this;
		},
		close: function(){
			this.getDialog().modal('hide');
		},
		destroy: function(){
			this.getDialog().remove();
		},
		createDialog: function(){
			var $dialog = $(document.createElement('div'));
			$dialog.addClass('modal fade hide');
			$('body').append($dialog);
			$dialog.modal({
				backdrop	:	this.options.backdrop,
				show		:	false
			});
			
			return $dialog;
		},
		setDialog: function($dialog){
			this.$dialog = $dialog;
			
			return this;
		},
		getDialog: function(){
			return this.$dialog;
		},
		createHeader: function(){
			var $header = $('<div class="modal-header"></div>');
			if (this.getTitle() == null) {
				$header.hide();
			}else{
				$header.append(this.createDynamicContent(this.getTitle()));
			}
			
			return $header;
		},
		setHeader: function($header){
			this.$header = $header;
			
			return this;
		},
		getHeader: function(){
			return this.$header;
		},
		createBody: function(){
			var $body = $('<div class="modal-body"></div>');
			if (this.getContent() != null) {
				$body.append(this.createDynamicContent(this.getContent()));
			}
			
			return $body;
		},
		setBody: function($body){
			this.$body = $body;
			
			return this;
		},
		getBody: function(){
			return this.$body;
		},
		createFooter: function(){
			return $('<div class="modal-footer"></div>');
		},
		setFooter: function($footer){
			this.$footer = $footer;
			
			return this;
		},
		getFooter: function(){
			return this.$footer;
		},
		setButton: function(id, $btn){
			this.buttons[id] = $btn;
			
			return this;
		},
		getButton: function(id){
			return this.buttons[id];
		},
		createDynamicContent: function(rawContent){
			var contentType = typeof rawContent;
			if (contentType != 'function') {
				return rawContent;
			}
			
			return rawContent(this);
		},
		setData: function(key, value){
			this.dataHolder[key] = value;
			
			return this;
		},
		getData: function(key){
			return this.dataHolder[key];
		},
		setTitle: function(title){
			this.options.title = title;
			
			return this;
		},
		getTitle: function(){
			return this.options.title;
		},
		setContent: function(content){
			this.options.content = content;
			
			return this;
		},
		getContent: function(){
			return this.options.content;
		},
		setButtons: function(buttons){
			this.options.buttons = buttons;
			
			return this;
		},
		getButtons: function(){
			return this.options.buttons;
		},
		setReady: function(readyCallback){
			this.options.ready = readyCallback;
			
			return this;
		},
		getReady: function(){
			return this.options.ready;
		},
		/**
		 * Run this after the dialog is ready
		 */
		ready: function(){
			if (typeof this.options.ready == 'function') {
				this.options.ready(this);
			}
			
			return this;
		}
	};
	
	/* ================================================
	 * For lazy people
	 * ================================================ */
	BootstrapDialog.alert = function(message, callback){
		new BootstrapDialog({
			content:	message,
			buttons:	[{
				label	:	'OK',
				cssClass:	'btn-primary',
				onclick	:	function(dialog){
					if (typeof callback == 'function') {
						callback();
					}
					dialog.close();
				}
			}]
		}).open();
	};
	
	BootstrapDialog.confirm = function(message, callback){
		new BootstrapDialog({
			content:	message,
			buttons:	[{
				label	:	'Cancel',
				onclick	:	function(dialog){
					if (typeof callback == 'function') {
						callback(false);
					}
					dialog.close();
				}
			}, {
				label	:	'OK',
				cssClass:	'btn-primary',
				onclick	:	function(dialog){
					if (typeof callback == 'function') {
						callback(true);
					}
					dialog.close();
				}
			}]
		}).open();
	};

}(window.jQuery);
