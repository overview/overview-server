(function() {
  if (!window.console) console = {};
  console.log = jasmine.log;

  Timecop.MockDate.now = function() {
    return (new Date()).getTime(); // the mocked date
  };
  Timecop.install();
})();
