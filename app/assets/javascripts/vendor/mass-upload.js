
define('MassUpload/FileInfo',[],function() {
  var FileInfo;
  FileInfo = (function() {
    function FileInfo(name, lastModifiedDate, total, loaded) {
      this.name = name;
      this.lastModifiedDate = lastModifiedDate;
      this.total = total;
      this.loaded = loaded;
    }

    return FileInfo;

  })();
  FileInfo.fromJson = function(obj) {
    return new FileInfo(obj.name, new Date(obj.lastModifiedDate), obj.total, obj.loaded);
  };
  FileInfo.fromFile = function(obj) {
    return new FileInfo(obj.name, obj.lastModifiedDate, obj.size, 0);
  };
  return FileInfo;
});

define('MassUpload/Upload',['backbone', './FileInfo'], function(Backbone, FileInfo) {
  var Upload;
  return Upload = (function() {
    Upload.prototype = Object.create(Backbone.Events);

    Upload.prototype.defaults = {
      file: null,
      fileInfo: null,
      error: null,
      uploading: false,
      deleting: false
    };

    function Upload(attributes) {
      var _ref, _ref1, _ref2, _ref3;
      this.file = (_ref = attributes.file) != null ? _ref : null;
      this.fileInfo = (_ref1 = attributes.fileInfo) != null ? _ref1 : null;
      this.error = (_ref2 = attributes.error) != null ? _ref2 : null;
      this.uploading = attributes.uploading || false;
      this.deleting = attributes.deleting || false;
      this.id = ((_ref3 = this.fileInfo) != null ? _ref3 : this.file).name;
      this.attributes = this;
    }

    Upload.prototype.get = function(attr) {
      return this[attr];
    };

    Upload.prototype.set = function(attrs) {
      var k, v;
      this._previousAttributes = new Upload(this);
      for (k in attrs) {
        v = attrs[k];
        this[k] = v;
      }
      this.trigger('change', this);
      return this._previousAttributes = null;
    };

    Upload.prototype.previousAttributes = function() {
      return this._previousAttributes;
    };

    Upload.prototype.size = function() {
      var _ref;
      return this._size != null ? this._size : this._size = (_ref = this.file) != null ? _ref.size : void 0;
    };

    Upload.prototype.lastModifiedDate = function() {
      var _ref;
      return this._lastModifiedDate != null ? this._lastModifiedDate : this._lastModifiedDate = (_ref = this.file) != null ? _ref.lastModifiedDate : void 0;
    };

    Upload.prototype.updateWithProgress = function(progressEvent) {
      var fileInfo;
      fileInfo = new FileInfo(this.id, this.lastModifiedDate(), progressEvent.total, progressEvent.loaded);
      return this.set({
        fileInfo: fileInfo
      });
    };

    Upload.prototype.getProgress = function() {
      if ((this.fileInfo != null) && !this.hasConflict()) {
        return {
          loaded: this.fileInfo.loaded,
          total: this.fileInfo.total
        };
      } else if (this.file != null) {
        return {
          loaded: 0,
          total: this.size()
        };
      }
    };

    Upload.prototype.isFullyUploaded = function() {
      return (this.fileInfo != null) && (this.error == null) && !this.uploading && !this.deleting && this.fileInfo.loaded === this.fileInfo.total;
    };

    Upload.prototype.hasConflict = function() {
      return (this.fileInfo != null) && (this.file != null) && (this.fileInfo.name !== this.id || this.fileInfo.total !== this.size() || this.fileInfo.lastModifiedDate.getTime() !== this.lastModifiedDate().getTime());
    };

    return Upload;

  })();
});

define('MassUpload/UploadCollection',['backbone', './Upload'], function(Backbone, Upload) {
  var UploadCollection, UploadPriorityQueue;
  UploadPriorityQueue = (function() {
    function UploadPriorityQueue() {
      this._clear();
    }

    UploadPriorityQueue.prototype._clear = function() {
      this.deleting = [];
      this.uploading = [];
      this.unfinished = [];
      return this.unstarted = [];
    };

    UploadPriorityQueue.prototype.uploadAttributesToState = function(uploadAttributes) {
      var ret;
      ret = uploadAttributes.error != null ? null : uploadAttributes.deleting ? 'deleting' : uploadAttributes.uploading ? 'uploading' : (uploadAttributes.file != null) && (uploadAttributes.fileInfo != null) && uploadAttributes.fileInfo.loaded < uploadAttributes.fileInfo.total ? 'unfinished' : (uploadAttributes.file != null) && (uploadAttributes.fileInfo == null) ? 'unstarted' : null;
      return ret;
    };

    UploadPriorityQueue.prototype.addBatch = function(uploads) {
      var state, upload, _i, _len;
      for (_i = 0, _len = uploads.length; _i < _len; _i++) {
        upload = uploads[_i];
        state = this.uploadAttributesToState(upload.attributes);
        if (state != null) {
          this[state].push(upload);
        }
      }
      return void 0;
    };

    UploadPriorityQueue.prototype._removeUploadFromArray = function(upload, array) {
      var idx;
      idx = array.indexOf(upload);
      if (idx >= 0) {
        return array.splice(idx, 1);
      }
    };

    UploadPriorityQueue.prototype.remove = function(upload) {
      var state;
      state = this.uploadAttributesToState(upload.attributes);
      if (state != null) {
        return this._removeUploadFromArray(upload.attributes, this[state]);
      }
    };

    UploadPriorityQueue.prototype.change = function(upload) {
      var newState, prevState;
      prevState = this.uploadAttributesToState(upload.previousAttributes());
      newState = this.uploadAttributesToState(upload.attributes);
      if (prevState !== newState) {
        if (prevState != null) {
          this._removeUploadFromArray(upload, this[prevState]);
        }
        if (newState != null) {
          return this[newState].push(upload);
        }
      }
    };

    UploadPriorityQueue.prototype.reset = function(uploads) {
      if (uploads == null) {
        uploads = [];
      }
      this._clear();
      return this.addBatch(uploads);
    };

    UploadPriorityQueue.prototype.next = function() {
      var _ref, _ref1, _ref2, _ref3;
      return (_ref = (_ref1 = (_ref2 = (_ref3 = this.deleting[0]) != null ? _ref3 : this.uploading[0]) != null ? _ref2 : this.unfinished[0]) != null ? _ref1 : this.unstarted[0]) != null ? _ref : null;
    };

    return UploadPriorityQueue;

  })();
  return UploadCollection = (function() {
    UploadCollection.prototype = Object.create(Backbone.Events);

    function UploadCollection() {
      this.models = [];
      this._priorityQueue = new UploadPriorityQueue();
      this.reset([]);
    }

    UploadCollection.prototype.each = function(func, context) {
      return this.models.forEach(func, context);
    };

    UploadCollection.prototype.map = function(func, context) {
      return this.models.map(func, context);
    };

    UploadCollection.prototype._prepareModel = function(upload) {
      if (upload instanceof Upload) {
        return upload;
      } else {
        return new Upload(upload);
      }
    };

    UploadCollection.prototype.reset = function(uploads) {
      var upload, _i, _j, _len, _len1, _ref, _ref1;
      _ref = this.models;
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        upload = _ref[_i];
        upload.off('all', this._onUploadEvent, this);
      }
      this.models = (function() {
        var _j, _len1, _results;
        _results = [];
        for (_j = 0, _len1 = uploads.length; _j < _len1; _j++) {
          upload = uploads[_j];
          _results.push(this._prepareModel(upload));
        }
        return _results;
      }).call(this);
      this.length = this.models.length;
      this._idToModel = {};
      _ref1 = this.models;
      for (_j = 0, _len1 = _ref1.length; _j < _len1; _j++) {
        upload = _ref1[_j];
        upload.on('all', this._onUploadEvent, this);
        this._idToModel[upload.id] = upload;
      }
      this._priorityQueue.reset(this.models);
      return this.trigger('reset', uploads);
    };

    UploadCollection.prototype.get = function(id) {
      var _ref;
      return (_ref = this._idToModel[id]) != null ? _ref : null;
    };

    UploadCollection.prototype.addFiles = function(files) {
      var file, uploads;
      uploads = (function() {
        var _i, _len, _results;
        _results = [];
        for (_i = 0, _len = files.length; _i < _len; _i++) {
          file = files[_i];
          _results.push(new Upload({
            file: file
          }));
        }
        return _results;
      })();
      return this._addWithMerge(uploads);
    };

    UploadCollection.prototype.addFileInfos = function(fileInfos) {
      var fileInfo, uploads;
      uploads = (function() {
        var _i, _len, _results;
        _results = [];
        for (_i = 0, _len = fileInfos.length; _i < _len; _i++) {
          fileInfo = fileInfos[_i];
          _results.push(new Upload({
            fileInfo: fileInfo
          }));
        }
        return _results;
      })();
      return this._addWithMerge(uploads);
    };

    UploadCollection.prototype.next = function() {
      return this._priorityQueue.next();
    };

    UploadCollection.prototype.add = function(uploadOrUploads) {
      if (uploadOrUploads.length != null) {
        return this.addBatch(uploadOrUploads);
      } else {
        return this.addBatch([uploadOrUploads]);
      }
    };

    UploadCollection.prototype.addBatch = function(uploads) {
      var upload, _i, _j, _len, _len1;
      for (_i = 0, _len = uploads.length; _i < _len; _i++) {
        upload = uploads[_i];
        this._idToModel[upload.id] = upload;
        upload.on('all', this._onUploadEvent, this);
        this.models.push(upload);
      }
      this.length += uploads.length;
      this._priorityQueue.addBatch(uploads);
      for (_j = 0, _len1 = uploads.length; _j < _len1; _j++) {
        upload = uploads[_j];
        this.trigger('add', upload);
      }
      return this.trigger('add-batch', uploads);
    };

    UploadCollection.prototype._onUploadEvent = function(event, model, collection, options) {
      if (event !== 'add' && event !== 'remove') {
        this.trigger.apply(this, arguments);
      }
      if (event === 'change') {
        return this._priorityQueue.change(model);
      }
    };

    UploadCollection.prototype._addWithMerge = function(uploads) {
      var existingUpload, file, fileInfo, toAdd, upload, _i, _len;
      toAdd = [];
      for (_i = 0, _len = uploads.length; _i < _len; _i++) {
        upload = uploads[_i];
        if ((existingUpload = this.get(upload.id)) != null) {
          file = upload.get('file');
          fileInfo = upload.get('fileInfo');
          if (file != null) {
            existingUpload.set({
              file: file
            });
          }
          if (fileInfo != null) {
            existingUpload.set({
              fileInfo: fileInfo
            });
          }
        } else {
          toAdd.push(upload);
        }
      }
      if (toAdd.length) {
        this.add(toAdd);
      }
      return void 0;
    };

    return UploadCollection;

  })();
});

define('MassUpload/FileLister',[],function() {
  var FileLister;
  return FileLister = (function() {
    function FileLister(doListFiles, callbacks) {
      this.doListFiles = doListFiles;
      this.callbacks = callbacks;
      this.running = false;
    }

    FileLister.prototype.run = function() {
      var _base,
        _this = this;
      if (this.running) {
        throw 'already running';
      }
      this.running = true;
      if (typeof (_base = this.callbacks).onStart === "function") {
        _base.onStart();
      }
      return this.doListFiles((function(progressEvent) {
        var _base1;
        return typeof (_base1 = _this.callbacks).onProgress === "function" ? _base1.onProgress(progressEvent) : void 0;
      }), (function(fileInfos) {
        return _this._onSuccess(fileInfos);
      }), (function(errorDetail) {
        return _this._onError(errorDetail);
      }));
    };

    FileLister.prototype._onSuccess = function(fileInfos) {
      var _base;
      if (typeof (_base = this.callbacks).onSuccess === "function") {
        _base.onSuccess(fileInfos);
      }
      return this._onStop();
    };

    FileLister.prototype._onError = function(errorDetail) {
      this.callbacks.onError(errorDetail);
      return this._onStop();
    };

    FileLister.prototype._onStop = function() {
      var _base;
      this.running = false;
      return typeof (_base = this.callbacks).onStop === "function" ? _base.onStop() : void 0;
    };

    return FileLister;

  })();
});

define('MassUpload/FileUploader',['./FileInfo'], function(FileInfo) {
  var FileUploader;
  return FileUploader = (function() {
    function FileUploader(doUpload, callbacks) {
      this.doUpload = doUpload;
      this.callbacks = callbacks;
      this._file = null;
      this._abortCallback = null;
      this._aborting = false;
    }

    FileUploader.prototype.run = function(file) {
      var _base,
        _this = this;
      if (this._file != null) {
        throw 'already running';
      }
      this._file = file;
      if (typeof (_base = this.callbacks).onStart === "function") {
        _base.onStart(this._file);
      }
      return this._abortCallback = this.doUpload(file, (function(progressEvent) {
        return _this._onProgress(file, progressEvent);
      }), (function() {
        return _this._onSuccess(file);
      }), (function(errorDetail) {
        return _this._onError(file, errorDetail);
      }));
    };

    FileUploader.prototype.abort = function() {
      if (this._file && !this._aborting) {
        this._aborting = true;
        if (typeof this._abortCallback === 'function') {
          return this._abortCallback();
        }
      }
    };

    FileUploader.prototype._onProgress = function(file, progressEvent) {
      var _base;
      return typeof (_base = this.callbacks).onProgress === "function" ? _base.onProgress(file, progressEvent) : void 0;
    };

    FileUploader.prototype._onSuccess = function(file) {
      var _base;
      if (typeof (_base = this.callbacks).onSuccess === "function") {
        _base.onSuccess(file);
      }
      return this._onStop(file);
    };

    FileUploader.prototype._onError = function(file, errorDetail) {
      var _base;
      if (typeof (_base = this.callbacks).onError === "function") {
        _base.onError(file, errorDetail);
      }
      return this._onStop(file);
    };

    FileUploader.prototype._onStop = function(file) {
      var _base;
      this._file = null;
      this._abortCallback = null;
      this._aborting = false;
      return typeof (_base = this.callbacks).onStop === "function" ? _base.onStop(file) : void 0;
    };

    return FileUploader;

  })();
});

define('MassUpload/FileDeleter',[],function() {
  var FileDeleter;
  return FileDeleter = (function() {
    function FileDeleter(doDeleteFile, callbacks) {
      this.doDeleteFile = doDeleteFile;
      this.callbacks = callbacks != null ? callbacks : {};
      this.running = false;
    }

    FileDeleter.prototype.run = function(fileInfo) {
      var _base,
        _this = this;
      if (this.running) {
        throw 'already running';
      }
      this.running = true;
      if (typeof (_base = this.callbacks).onStart === "function") {
        _base.onStart(fileInfo);
      }
      return this.doDeleteFile(fileInfo, (function() {
        return _this._onSuccess(fileInfo);
      }), (function(errorDetail) {
        return _this._onError(fileInfo, errorDetail);
      }));
    };

    FileDeleter.prototype._onSuccess = function(fileInfo) {
      var _base;
      if (typeof (_base = this.callbacks).onSuccess === "function") {
        _base.onSuccess(fileInfo);
      }
      return this._onStop(fileInfo);
    };

    FileDeleter.prototype._onError = function(fileInfo, errorDetail) {
      var _base;
      if (typeof (_base = this.callbacks).onError === "function") {
        _base.onError(fileInfo, errorDetail);
      }
      return this._onStop(fileInfo);
    };

    FileDeleter.prototype._onStop = function(fileInfo) {
      var _base;
      this.running = false;
      if (typeof (_base = this.callbacks).onStop === "function") {
        _base.onStop(fileInfo);
      }
      return void 0;
    };

    return FileDeleter;

  })();
});

define('MassUpload/State',[],function() {
  var State;
  return State = (function() {
    function State(attrs) {
      var _ref, _ref1, _ref2, _ref3;
      if (attrs == null) {
        attrs = {};
      }
      this.loaded = (_ref = attrs.loaded) != null ? _ref : 0;
      this.total = (_ref1 = attrs.total) != null ? _ref1 : 0;
      this.status = (_ref2 = attrs.status) != null ? _ref2 : 'waiting';
      this.errors = (_ref3 = attrs.errors) != null ? _ref3 : [];
    }

    State.prototype._extend = function(attrs) {
      var _ref, _ref1, _ref2, _ref3;
      return new State({
        loaded: (_ref = attrs.loaded) != null ? _ref : this.loaded,
        total: (_ref1 = attrs.total) != null ? _ref1 : this.total,
        status: (_ref2 = attrs.status) != null ? _ref2 : this.status,
        errors: (_ref3 = attrs.errors) != null ? _ref3 : this.errors
      });
    };

    State.prototype.isComplete = function() {
      return this.total && this.loaded === this.total && this.status === 'waiting' && !this.errors.length && true || false;
    };

    State.prototype.withTotal = function(total) {
      return this._extend({
        total: total
      });
    };

    State.prototype.withLoaded = function(loaded) {
      return this._extend({
        loaded: loaded
      });
    };

    State.prototype.withStatus = function(status) {
      return this._extend({
        status: status
      });
    };

    State.prototype.withAnError = function(error) {
      var newErrors;
      newErrors = this.errors.slice(0);
      newErrors.push(error);
      return this._extend({
        errors: newErrors
      });
    };

    State.prototype.withoutAnError = function(error) {
      var index, newErrors;
      newErrors = this.errors.slice(0);
      index = newErrors.indexOf(error);
      newErrors.splice(index, 1);
      return this._extend({
        errors: newErrors
      });
    };

    return State;

  })();
});

define('MassUpload/UploadProgress',['backbone'], function(Backbone) {
  return Backbone.Model.extend({
    defaults: {
      loaded: 0,
      total: 0
    },
    initialize: function() {
      var collection;
      collection = this.get('uploadCollection');
      if (collection == null) {
        throw 'Must initialize UploadProgress with `uploadCollection`, an UploadCollection';
      }
      this._idToLastKnownProgress = {};
      return this._updateAndStartListening();
    },
    _adjust: function(dLoaded, dTotal) {
      return this.set({
        loaded: this.get('loaded') + dLoaded,
        total: this.get('total') + dTotal
      });
    },
    add: function(model) {
      var progress;
      progress = model.getProgress();
      this._adjust(progress.loaded, progress.total);
      return this._idToLastKnownProgress[model.id] = progress;
    },
    reset: function(collection) {
      var idToLastKnownProgress, loaded, model, progress, total, _i, _len, _ref;
      idToLastKnownProgress = this._idToLastKnownProgress = {};
      loaded = 0;
      total = 0;
      _ref = collection.models;
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        model = _ref[_i];
        progress = model.getProgress();
        idToLastKnownProgress[model.id] = progress;
        loaded += progress.loaded;
        total += progress.total;
      }
      return this.set({
        loaded: loaded,
        total: total
      });
    },
    remove: function(model) {
      var progress;
      progress = model.getProgress();
      this._adjust(-progress.loaded, -progress.total);
      return this._idToLastKnownProgress[model.id] = progress;
    },
    change: function(model) {
      var newProgress, oldProgress;
      oldProgress = this._idToLastKnownProgress[model.id];
      if (oldProgress != null) {
        newProgress = model.getProgress();
        this._adjust(newProgress.loaded - oldProgress.loaded, newProgress.total - oldProgress.total);
        return this._idToLastKnownProgress[model.id] = newProgress;
      }
    },
    _updateAndStartListening: function() {
      var collection, event, _i, _len, _ref;
      collection = this.get('uploadCollection');
      _ref = ['add', 'remove', 'change', 'reset'];
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        event = _ref[_i];
        this.listenTo(collection, event, this[event]);
      }
      this.reset(collection);
      return void 0;
    },
    inBatch: function(callback) {
      this.stopListening(this.get('uploadCollection'));
      try {
        return callback();
      } finally {
        this._updateAndStartListening();
      }
    }
  });
});

var __hasProp = {}.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

define('MassUpload',['backbone', 'underscore', 'MassUpload/UploadCollection', 'MassUpload/FileLister', 'MassUpload/FileUploader', 'MassUpload/FileDeleter', 'MassUpload/State', 'MassUpload/UploadProgress'], function(Backbone, _, UploadCollection, FileLister, FileUploader, FileDeleter, State, UploadProgress) {
  var MassUpload;
  return MassUpload = (function(_super) {
    __extends(MassUpload, _super);

    MassUpload.prototype.defaults = function() {
      return {
        status: 'waiting',
        listFilesProgress: null,
        listFilesError: null,
        uploadProgress: null,
        uploadErrors: []
      };
    };

    function MassUpload(options) {
      this._removedUploads = [];
      MassUpload.__super__.constructor.call(this, {}, options);
    }

    MassUpload.prototype.initialize = function(attributes, options) {
      var resetUploadProgress, _ref,
        _this = this;
      this._options = options;
      this.uploads = (_ref = options != null ? options.uploads : void 0) != null ? _ref : new UploadCollection();
      this._uploadProgress = new UploadProgress({
        uploadCollection: this.uploads
      });
      resetUploadProgress = function() {
        return _this.set({
          uploadProgress: _this._uploadProgress.pick('loaded', 'total')
        });
      };
      this.listenTo(this._uploadProgress, 'change', resetUploadProgress);
      resetUploadProgress();
      this.listenTo(this.uploads, 'add-batch', this._onUploadBatchAdded);
      this.listenTo(this.uploads, 'change', function(upload) {
        return _this._onUploadChanged(upload);
      });
      this.listenTo(this.uploads, 'reset', function() {
        return _this._onUploadsReset();
      });
      return this.prepare();
    };

    MassUpload.prototype.prepare = function() {
      var options, _ref, _ref1, _ref2,
        _this = this;
      options = this._options;
      this.lister = (_ref = options != null ? options.lister : void 0) != null ? _ref : new FileLister(options.doListFiles);
      this.lister.callbacks = {
        onStart: function() {
          return _this._onListerStart();
        },
        onProgress: function(progressEvent) {
          return _this._onListerProgress(progressEvent);
        },
        onSuccess: function(fileInfos) {
          return _this._onListerSuccess(fileInfos);
        },
        onError: function(errorDetail) {
          return _this._onListerError(errorDetail);
        },
        onStop: function() {
          return _this._onListerStop();
        }
      };
      this.uploader = (_ref1 = options != null ? options.uploader : void 0) != null ? _ref1 : new FileUploader(options.doUploadFile);
      this.uploader.callbacks = {
        onStart: function(file) {
          return _this._onUploaderStart(file);
        },
        onStop: function(file) {
          return _this._onUploaderStop(file);
        },
        onSuccess: function(file) {
          return _this._onUploaderSuccess(file);
        },
        onError: function(file, errorDetail) {
          return _this._onUploaderError(file, errorDetail);
        },
        onProgress: function(file, progressEvent) {
          return _this._onUploaderProgress(file, progressEvent);
        }
      };
      this.deleter = (_ref2 = options != null ? options.deleter : void 0) != null ? _ref2 : new FileDeleter(options.doDeleteFile);
      return this.deleter.callbacks = {
        onStart: function(fileInfo) {
          return _this._onDeleterStart(fileInfo);
        },
        onSuccess: function(fileInfo) {
          return _this._onDeleterSuccess(fileInfo);
        },
        onError: function(fileInfo, errorDetail) {
          return _this._onDeleterError(fileInfo, errorDetail);
        },
        onStop: function(fileInfo) {
          return _this._onDeleterStop(fileInfo);
        }
      };
    };

    MassUpload.prototype.fetchFileInfosFromServer = function() {
      return this.lister.run();
    };

    MassUpload.prototype.retryListFiles = function() {
      return this.fetchFileInfosFromServer();
    };

    MassUpload.prototype.retryUpload = function(upload) {
      return upload.set({
        error: null
      });
    };

    MassUpload.prototype.retryAllUploads = function() {
      return this.uploads.each(function(upload) {
        return upload.set({
          error: null
        });
      });
    };

    MassUpload.prototype.addFiles = function(files) {
      var _this = this;
      return this._uploadProgress.inBatch(function() {
        return _this.uploads.addFiles(files);
      });
    };

    MassUpload.prototype.removeUpload = function(upload) {
      return upload.set({
        deleting: true
      });
    };

    MassUpload.prototype.abort = function() {
      var _this = this;
      this.uploads.each(function(upload) {
        return _this.removeUpload(upload);
      });
      this.uploads.reset();
      return this.prepare();
    };

    MassUpload.prototype._onListerStart = function() {
      return this.set({
        status: 'listing-files',
        listFilesError: null
      });
    };

    MassUpload.prototype._onListerProgress = function(progressEvent) {
      return this.set({
        listFilesProgress: progressEvent
      });
    };

    MassUpload.prototype._onListerSuccess = function(fileInfos) {
      this.uploads.addFileInfos(fileInfos);
      return this._tick();
    };

    MassUpload.prototype._onListerError = function(errorDetail) {
      return this.set({
        listFilesError: errorDetail,
        status: 'listing-files-error'
      });
    };

    MassUpload.prototype._onListerStop = function() {};

    MassUpload.prototype._mergeUploadError = function(upload, prevError, curError) {
      var index, newErrors;
      newErrors = this.get('uploadErrors').slice(0);
      index = _.sortedIndex(newErrors, {
        upload: upload
      }, function(x) {
        return x.upload.id;
      });
      if (prevError == null) {
        newErrors.splice(index, 0, {
          upload: upload,
          error: curError
        });
      } else if (curError == null) {
        newErrors.splice(index, 1);
      } else {
        newErrors[index].error = curError;
      }
      return this.set({
        uploadErrors: newErrors
      });
    };

    MassUpload.prototype._onUploadBatchAdded = function(uploads) {
      var error, upload, _i, _len;
      for (_i = 0, _len = uploads.length; _i < _len; _i++) {
        upload = uploads[_i];
        error = upload.get('error');
        if (error != null) {
          this._mergeUploadError(upload, null, error);
        }
      }
      return this._forceBestTick();
    };

    MassUpload.prototype._onUploadChanged = function(upload) {
      var deleting1, deleting2, error1, error2;
      error1 = upload.previousAttributes().error;
      error2 = upload.get('error');
      if (error1 !== error2) {
        this._mergeUploadError(upload, error1, error2);
      }
      deleting1 = upload.previousAttributes().deleting;
      deleting2 = upload.get('deleting');
      if (deleting2 && !deleting1) {
        this._removedUploads.push(upload);
      }
      return this._forceBestTick();
    };

    MassUpload.prototype._onUploadsReset = function() {
      var newErrors;
      newErrors = [];
      this.uploads.each(function(upload) {
        var error;
        if ((error = upload.get('error'))) {
          return newErrors.push({
            upload: upload,
            error: error
          });
        }
      });
      this.set({
        uploadErrors: newErrors
      });
      return this._tick();
    };

    MassUpload.prototype._onUploaderStart = function(file) {
      var upload;
      upload = this.uploads.get(file.name);
      return upload.set({
        uploading: true,
        error: null
      });
    };

    MassUpload.prototype._onUploaderStop = function(file) {
      var upload;
      upload = this.uploads.get(file.name);
      upload.set({
        uploading: false
      });
      return this._tick();
    };

    MassUpload.prototype._onUploaderProgress = function(file, progressEvent) {
      var upload;
      upload = this.uploads.get(file.name);
      return upload.updateWithProgress(progressEvent);
    };

    MassUpload.prototype._onUploaderError = function(file, errorDetail) {
      var upload;
      upload = this.uploads.get(file.name);
      return upload.set({
        error: errorDetail
      });
    };

    MassUpload.prototype._onUploaderSuccess = function(file) {
      var upload;
      upload = this.uploads.get(file.name);
      return upload.updateWithProgress({
        loaded: upload.size(),
        total: upload.size()
      });
    };

    MassUpload.prototype._onDeleterStart = function(fileInfo) {
      return this.set({
        status: 'uploading'
      });
    };

    MassUpload.prototype._onDeleterSuccess = function(fileInfo) {
      var upload;
      upload = this.uploads.get(fileInfo.name);
      return this.uploads.remove(upload);
    };

    MassUpload.prototype._onDeleterError = function(fileInfo, errorDetail) {
      var upload;
      upload = this.uploads.get(fileInfo.name);
      return upload.set({
        error: errorDetail
      });
    };

    MassUpload.prototype._onDeleterStop = function(fileInfo) {
      return this._tick();
    };

    MassUpload.prototype._tick = function() {
      var progress, status, upload;
      upload = this.uploads.next();
      this._currentUpload = upload;
      if (upload != null) {
        if (upload.get('deleting')) {
          this.deleter.run(upload.get('fileInfo'));
        } else {
          this.uploader.run(upload.get('file'));
        }
      }
      status = this.get('uploadErrors').length ? 'uploading-error' : upload != null ? 'uploading' : (progress = this.get('uploadProgress'), progress.loaded === progress.total ? 'waiting' : 'waiting-error');
      return this.set({
        status: status
      });
    };

    MassUpload.prototype._forceBestTick = function() {
      var upload;
      upload = this.uploads.next();
      if (upload !== this._currentUpload) {
        if (this._currentUpload) {
          return this.uploader.abort();
        } else {
          return this._tick();
        }
      }
    };

    return MassUpload;

  })(Backbone.Model);
});

define('mass-upload',['MassUpload'], function(MassUpload) {
  return MassUpload;
});
