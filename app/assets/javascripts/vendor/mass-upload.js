
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
  return Backbone.Model.extend({
    defaults: {
      file: null,
      fileInfo: null,
      error: null,
      uploading: false,
      deleting: false
    },
    initialize: function(attributes) {
      var fileLike, id, _ref;
      fileLike = (_ref = attributes.file) != null ? _ref : attributes.fileInfo;
      id = fileLike.name;
      return this.set({
        id: id
      });
    },
    updateWithProgress: function(progressEvent) {
      var fileInfo;
      fileInfo = FileInfo.fromFile(this.get('file'));
      fileInfo.loaded = progressEvent.loaded;
      fileInfo.total = progressEvent.total;
      return this.set('fileInfo', fileInfo);
    },
    getProgress: function() {
      var file, fileInfo;
      if (((fileInfo = this.get('fileInfo')) != null) && !this.hasConflict()) {
        return {
          loaded: fileInfo.loaded,
          total: fileInfo.total
        };
      } else if ((file = this.get('file')) != null) {
        return {
          loaded: 0,
          total: file.size
        };
      }
    },
    isFullyUploaded: function() {
      var error, fileInfo;
      fileInfo = this.get('fileInfo');
      error = this.get('error');
      return !this.get('uploading') && !this.get('deleting') && (this.get('error') == null) && (fileInfo != null) && fileInfo.loaded === fileInfo.total;
    },
    hasConflict: function() {
      var file, fileInfo;
      fileInfo = this.get('fileInfo');
      file = this.get('file');
      return (fileInfo != null) && (file != null) && (fileInfo.name !== file.name || fileInfo.lastModifiedDate.getTime() !== file.lastModifiedDate.getTime() || fileInfo.total !== file.size);
    }
  });
});

define('MassUpload/UploadCollection',['backbone', './Upload'], function(Backbone, Upload) {
  return Backbone.Collection.extend({
    model: Upload,
    comparator: 'id',
    addFiles: function(files) {
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
    },
    addFileInfos: function(fileInfos) {
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
    },
    next: function() {
      var firstDeleting, firstUnfinished, firstUnstarted, firstUploading;
      firstDeleting = null;
      firstUploading = null;
      firstUnfinished = null;
      firstUnstarted = null;
      this.each(function(upload) {
        var file, fileInfo;
        file = upload.get('file');
        fileInfo = upload.get('fileInfo');
        if (upload.get('error') == null) {
          if (upload.get('deleting')) {
            firstDeleting || (firstDeleting = upload);
          }
          if (upload.get('uploading')) {
            firstUploading || (firstUploading = upload);
          }
          if (file && fileInfo && fileInfo.loaded < fileInfo.total) {
            firstUnfinished || (firstUnfinished = upload);
          }
          if (file && !fileInfo) {
            return firstUnstarted || (firstUnstarted = upload);
          }
        }
      });
      return firstDeleting || firstUploading || firstUnfinished || firstUnstarted;
    },
    _addWithMerge: function(uploads) {
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
      return this.add(toAdd);
    }
  });
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
      var add, adjust, callback, change, cidToLastKnownProgress, collection, eventName, events, remove, reset,
        _this = this;
      collection = this.get('collection');
      if (collection == null) {
        throw 'Must initialize UploadProgress with `collection`, an UploadCollection';
      }
      adjust = function(dLoaded, dTotal) {
        _this.set({
          loaded: _this.get('loaded') + dLoaded,
          total: _this.get('total') + dTotal
        });
        return void 0;
      };
      cidToLastKnownProgress = {};
      add = function(model) {
        var progress;
        progress = model.getProgress();
        adjust(progress.loaded, progress.total);
        return cidToLastKnownProgress[model.cid] = progress;
      };
      remove = function(model) {
        var progress;
        progress = cidToLastKnownProgress[model.cid];
        adjust(-progress.loaded, -progress.total);
        return delete cidToLastKnownProgress[model.cid];
      };
      change = function(model) {
        var newProgress, oldProgress;
        oldProgress = cidToLastKnownProgress[model.cid];
        if (oldProgress != null) {
          newProgress = model.getProgress();
          adjust(newProgress.loaded - oldProgress.loaded, newProgress.total - oldProgress.total);
          return cidToLastKnownProgress[model.cid] = newProgress;
        }
      };
      reset = function() {
        var progress;
        cidToLastKnownProgress = {};
        progress = {
          loaded: 0,
          total: 0
        };
        _this.get('collection').each(function(model) {
          var modelProgress;
          modelProgress = model.getProgress();
          cidToLastKnownProgress[model.cid] = modelProgress;
          progress.loaded += modelProgress.loaded;
          return progress.total += modelProgress.total;
        });
        return _this.set(progress);
      };
      events = {
        add: add,
        remove: remove,
        change: change,
        reset: reset
      };
      for (eventName in events) {
        callback = events[eventName];
        collection.on(eventName, callback);
      }
      reset();
      return void 0;
    }
  });
});

define('MassUpload',['backbone', 'MassUpload/UploadCollection', 'MassUpload/FileLister', 'MassUpload/FileUploader', 'MassUpload/FileDeleter', 'MassUpload/State', 'MassUpload/UploadProgress'], function(Backbone, UploadCollection, FileLister, FileUploader, FileDeleter, State, UploadProgress) {
  return Backbone.Model.extend({
    defaults: function() {
      return {
        status: 'waiting',
        listFilesProgress: null,
        listFilesError: null,
        uploadProgress: null,
        uploadErrors: []
      };
    },
    constructor: function(options) {
      this._removedUploads = [];
      return Backbone.Model.call(this, {}, options);
    },
    initialize: function(attributes, options) {
      var resetUploadProgress, uploadProgress, _ref, _ref1, _ref2, _ref3,
        _this = this;
      this.uploads = (_ref = options != null ? options.uploads : void 0) != null ? _ref : new UploadCollection();
      this.lister = (_ref1 = options != null ? options.lister : void 0) != null ? _ref1 : new FileLister(options.doListFiles);
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
      this.uploader = (_ref2 = options != null ? options.uploader : void 0) != null ? _ref2 : new FileUploader(options.doUploadFile);
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
      this.deleter = (_ref3 = options != null ? options.deleter : void 0) != null ? _ref3 : new FileDeleter(options.doDeleteFile);
      this.deleter.callbacks = {
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
      this.uploads.on('add change:file', function(upload) {
        return _this._onUploadAdded(upload);
      });
      this.uploads.on('change:deleting', function(upload) {
        return _this._onUploadDeleted(upload);
      });
      this.uploads.on('remove', function(upload) {
        return _this._onUploadRemoved(upload);
      });
      uploadProgress = new UploadProgress({
        collection: this.uploads
      });
      resetUploadProgress = function() {
        return _this.set({
          uploadProgress: uploadProgress.pick('loaded', 'total')
        });
      };
      uploadProgress.on('change', resetUploadProgress);
      return resetUploadProgress();
    },
    fetchFileInfosFromServer: function() {
      return this.lister.run();
    },
    retryListFiles: function() {
      return this.fetchFileInfosFromServer();
    },
    addFiles: function(files) {
      return this.uploads.addFiles(files);
    },
    removeUpload: function(upload) {
      return upload.set('deleting', true);
    },
    _onListerStart: function() {
      this.set('status', 'listing-files');
      return this.set('listFilesError', null);
    },
    _onListerProgress: function(progressEvent) {
      return this.set('listFilesProgress', progressEvent);
    },
    _onListerSuccess: function(fileInfos) {
      this.uploads.addFileInfos(fileInfos);
      return this._tick();
    },
    _onListerError: function(errorDetail) {
      this.set('listFilesError', errorDetail);
      return this.set('status', 'listing-files-error');
    },
    _onListerStop: function() {},
    _onUploadAdded: function(upload) {
      var status;
      status = this.get('status');
      if (status === 'uploading' || status === 'uploading-error') {
        return this.uploader.abort();
      } else {
        return this._tick();
      }
    },
    _onUploadRemoved: function(upload) {},
    _onUploadDeleted: function(upload) {
      var status;
      this._removedUploads.push(upload);
      status = this.get('status');
      if (status === 'uploading' || status === 'uploading-error') {
        return this.uploader.abort();
      } else {
        return this._tick();
      }
    },
    _onUploaderStart: function(file) {
      var upload;
      this.set('status', 'uploading');
      upload = this.uploads.get(file.name);
      return upload.set({
        uploading: true,
        error: null
      });
    },
    _onUploaderStop: function(file) {
      var upload;
      upload = this.uploads.get(file.name);
      upload.set('uploading', false);
      return this._tick();
    },
    _onUploaderProgress: function(file, progressEvent) {
      var upload;
      upload = this.uploads.get(file.name);
      return upload.updateWithProgress(progressEvent);
    },
    _onUploaderError: function(file, errorDetail) {
      var upload;
      upload = this.uploads.get(file.name);
      return upload.set('error', errorDetail);
    },
    _onUploaderSuccess: function(file) {},
    _onDeleterStart: function(fileInfo) {
      return this.set('status', 'uploading');
    },
    _onDeleterSuccess: function(fileInfo) {
      var upload;
      upload = this.uploads.get(fileInfo.name);
      return this.uploads.remove(upload);
    },
    _onDeleterError: function(fileInfo, errorDetail) {
      var upload;
      upload = this.uploads.get(fileInfo.name);
      return upload.set('error', errorDetail);
    },
    _onDeleterStop: function(fileInfo) {
      return this._tick();
    },
    _tick: function() {
      var upload;
      upload = this.uploads.next();
      if (upload != null) {
        if (upload.get('deleting')) {
          return this.deleter.run(upload.get('fileInfo'));
        } else {
          return this.uploader.run(upload.get('file'));
        }
      } else {
        return this.set('status', 'waiting');
      }
    }
  });
});

define('mass-upload',['MassUpload'], function(MassUpload) {
  return MassUpload;
});
