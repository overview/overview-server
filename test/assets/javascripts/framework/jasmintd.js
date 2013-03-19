/**
 * Extension specifics specific to the manipulation of Functions.
 *
 * @author: Sam Skjonsberg <sskjonsberg@opencar.com>
 */

var jasmintd = jasmintd || {};

/**
 * @description
 * Wraps the specified method with the specified method such that when executed, the second method is executed with
 * the first as it's first parameter.
 *
 * @param   {Function}  orig    The method to wrap.
 * @param   {Function}  func    The method to wrap the original function in.
 *
 * @returns {Function}  A new Function, which when executed passes the original Function instance
 *                      as the first parameter to the provided Function, as well as the arguments provided
 *                      at execution time, in the order originally provided.
 */
jasmintd.Function = {
    wrap :  function(orig, func) {
        return function() {
            return func.apply(
                this,
                [ orig.bind(this) ].concat(Array.prototype.slice.call(arguments, 0))
            );
        };
    }
};
/**
 * Extensions of jasmine.Suite for supporting Suite requirements.
 *
 * @author: Sam Skjonsberg <sskjonsberg@opencar.com>
 */

if(!jasmine || !jasmine.Block) {
    throw Error('Core jasmine Library not defined!');
}

/**
 * @function
 * @description
 * Overwrite the default Jasmine.Block.prototype.execute method so that when executing blocks the requirements associated
 * with the parent suite(s) are provided as arguments to the block.
 *
 *
 * @param   {Function}  complete    The function to execute once the block has been executed.
 *
 * @return  {void}
 */
jasmine.Block.prototype.execute = jasmintd.Function.wrap(
    jasmine.Block.prototype.execute,
    function(orig, complete) {
        this.func = jasmintd.Function.wrap(
            this.func,
            function(orig) {
                return orig.apply(this, this.suite.getRequirementExports());
            }
        );
        return orig(complete);
    }
);
/**
 * Define a custom class representing the requirements of a jasmine suite.
 *
 * @author: Sam Skjonsberg <sskjonsberg@opencar.com>
 */

if(!jasmine) {
    throw Error('Core jasmine Library not defined!');
}

(function() {

    /**
     * @class
     * @description
     * Class representing the script requirements of a given jasmine suite.
     *
     * @param   {Array} reqs    Array of script requirements.
     *
     * @constructor
     */
    jasmine.SuiteRequirements = function(reqs) {
        this.reqs = reqs;
    };

    /**
     * @function
     * @description
     * Loads the suite requirements and keep references to any associated exports.
     *
     * @param   {Function}  complete        A function to execute when the suite requirements have been loaded.
     *
     * @return  {jasmine.SuiteRequirements} The suite requirements.
     */
    jasmine.SuiteRequirements.prototype.execute = function(complete) {
        require(
            this.reqs,
            function() {
                this.exports = Array.prototype.slice.call(arguments);
                if(typeof complete === 'function') {
                    complete();
                }
            }.bind(this),
            function(err) {
                if(typeof complete === 'function') {
                    complete();
                }
            }.bind(this)
        );
        return this;
    };

}());
/**
 * Extensions of jasmine.Suite for supporting Suite requirements.
 *
 * @author: Sam Skjonsberg <sskjonsberg@opencar.com>
 */

if(!jasmine || !jasmine.Suite) {
    throw Error('Core jasmine Library not defined!');
}

(function() {
    var reSpacePrePeriod    = /\s+[.]+/g;

    /**
     * @function
     * @description
     * Adds the specified suite requirements.
     *
     * @param   {Array} reqs    Array of script requirements to add.
     *
     * @return  {jasmine.Suite} The suite.
     */
    jasmine.Suite.prototype.addRequirements = function(reqs) {
        this.reqs = new jasmine.SuiteRequirements(reqs);
        this.queue.addBefore(this.reqs);
        return this;
    };

    /**
     * @function
     * @description
     * Returns the exports defined by the suite's associated script requirements as an array.
     *
     * @return {Array}  Array of the exports defined by the suite's associated script requirements.
     */
    jasmine.Suite.prototype.getRequirementExports = function() {
        var suite   = this,
            exports = [];
        do {
            if(suite.reqs && suite.reqs.exports) {
                exports = exports.concat(suite.reqs.exports);
            }
            suite = suite.parentSuite;
        } while(suite);
        return exports;
    };

    /**
     * @function
     * @description
     * Extended jasmine.Suite.prototype.getFullName which provides support for custom formatting that meshes better
     * with JSTD's console output.
     *
     * @param   {Boolean}   formatForJSTD   Boolean indicating whether the full name should be formatted for JSTD output.
     *
     * @return  {String}    The suite's full name.
     */
    jasmine.Suite.prototype.getFullName = jasmintd.Function.wrap(
        jasmine.Suite.prototype.getFullName,
        function(orig, formatForJSTD) {
            var name = orig();
            return formatForJSTD ? name.replace(reSpacePrePeriod, '.') : name;
        }
    );

}());
var jasmintd = jasmintd || {};

(function() {

    var reLineEnd           = /\n+/,
        reTrailingPeriod    = /[.]+$/,
        reJasmine           = /\/jasmintd/,
        reTestUrl           = /https?:\/\/\w+(:\d+)?\/test\//,
        /**
         * @enum
         * @description
         * Collection of possible colors for output.
         */
        Colors = {
            PINK    : '\033[95m',
            YELLOW  : '\033[93m',
            RED     : '\033[91m',
            END     : '\033[0m'
        },
        /**
         * @private
         * @description
         * Sets the specified string to be output in the specified color.
         *
         * @param   {String}    str     The string.
         * @param   {String}    color   The color to set.
         *
         * @return {String}     The string in the specified color.
         */
        setColor = function(str, color) {
            return [ color,  str,  Colors.END ].join('');
        };


    /**
     * @class
     * @description
     * Custom reporter which handles translating Jasmine test results to JSTestDriver's reporter.
     *
     * @param   {Function}  done        Callback to execute when an individual spec is complete.
     * @param   {Function}  completed   Callback to execute when the entire test suite is complete.
     * @constructor
     */
    jasmintd.JSTDReporter = function(done, completed) {
        this.done           = done;
        this.completed      = completed;
    };
    jasmine.util.inherit(jasmintd.JSTDReporter, jasmine.Reporter);

    /**
     * @description
     * Fires when a spec execution begins.
     *
     * @return {void}
     */
    jasmintd.JSTDReporter.prototype.reportSpecStarting = function() {
        this.start = Date.now();
    };

    /**
     * @description
     * Fires when spec execution is complete.
     *
     * @param   {jasmine.Spec}  spec    The spec which just finished executing.
     *
     * @return  {void}
     */
    jasmintd.JSTDReporter.prototype.reportSpecResults = function(spec) {
        var elapsed     = Date.now() - this.start,
            results     = spec.results(),
            logMessages = [],
            messages    = [],
            item,
            items,
            i,
            ii;
        if(results.skipped) {
            return;
        }
        items = results.getItems();
        for(i = 0, ii = items.length; i < ii; i++) {
            item = items[i];
            if(item instanceof jasmine.MessageResult) {
                logMessages.push(setColor(item.toString(), Colors.YELLOW));
            } else if(!item.passed()) {
                messages.push(
                    {
                        // The message or name doesn't end up being formatted all that well by JSTD, especially if there's multiple.
                        // So we just let the stack do the work for us...
                        message : '',
                        name    : '',
                        stack   : this.formatErrorStack(item.trace.stack)
                    }
                );
            }
        }
        // Pass our results to JSTD.  For documentation of jstestdriver.TestResult see:
        // @see http://code.google.com/p/js-test-driver/source/browse/JsTestDriver/src/com/google/jstestdriver/javascript/TestResult.js?r=9222e94ae1d89532b4b0de2d6b43e6c11ae0060f
        this.done(
            new jstestdriver.TestResult(
                // We pass the name and description as a single string to JSTD so that it doesn't look like:
                // "Name.description".
                this.formatTestName(spec.suite.getFullName(true)),
                spec.description,
                results.failedCount > 0 ? jstestdriver.TestResult.RESULT.FAILED : jstestdriver.TestResult.RESULT.PASSED,
                jstestdriver.angular.toJson(messages),
                logMessages.join('\n'),
                elapsed
            )
        );
    };

    /**
     * @description
     * Fires when the entire suite is finished executing.
     *
     * @return {void}
     */
    jasmintd.JSTDReporter.prototype.reportRunnerResults = function() {
        // Execute the completed callback, when signals JSTD that execution is complete.
        this.completed();
    };

    /**
     * @description
     * Formats the test name for better presentation on the terminal within JSTD's output.
     *
     * @param   {String}    name    The name to format.
     *
     * @return  {String}    The formatted name.
     */
    jasmintd.JSTDReporter.prototype.formatTestName = function(name) {
        return setColor(name.replace(reTrailingPeriod, ''), Colors.PINK) + '..';
    };

    /**
     * @description
     * Formats the error stack associated with a test failure by stripping out portions of the stack trace
     * that are related to the internal jasmine library.
     *
     * @param   {String}    stack   The stack trace to format.
     *
     * @return  {String}    The formatted stack trace.
     */
    jasmintd.JSTDReporter.prototype.formatErrorStack = function(stack) {
        var line,
            lines = (stack || '').split(reLineEnd),
            i,
            ii,
            result = [];
        for(i = 0, ii = lines.length; i < ii; i++) {
            line = lines[i];
            if(line.length > 0 && !line.match(reJasmine)) {
                result.push(
                    setColor(line.replace(reTestUrl, ''), Colors.RED)
                );
            }
        }
        return result.join('\n');
    };

}());
/**
 * Adapter for running Jasmine BDD unit tests using JsTestDriver and requirejs.
 *
 * @author: Sam Skjonsberg <sskjonsberg@opencar.com>
 */
(function() {

    var TestType = {
            JASMINE : 'jasmine'
        };

    /**
     * @private
     * @description
     * Custom describe method which provides support for our special syntax where we provide the tests reqs
     * in it's definition.
     *
     * @param   {Function}          orig    The original describe function.
     * @param   {String}            name    The suite name.
     * @param   {Array}             [reqs]  Optional Array of script requirements.
     * @param   {Function}          fn      The suite definition.
     *
     * @return  {void}
     */
    describe = jasmintd.Function.wrap(
        describe,
        function(orig, name, reqs, fn) {
            var suite;
            if(typeof reqs === 'function') {
                fn      = reqs;
                reqs    = [];
            }
            return orig(name, fn).addRequirements(reqs);
        }
    );

    /**
     * @private
     * @description
     * Registers the Jasmine / RequireJS Adapter Plugin.
     */
    jstestdriver.pluginRegistrar.register(
        {
            /** @type {String} */
            name : "Jasmine and RequireJS Adapter",
            /**
             * @private
             * @description
             * Fires whenever we execute a test.  Here we wire up our custom JSTDReporter and pass it the done and complete
             * callbacks, so that it can notify JSTD when a test has finished executing.
             *
             * @param   {jstestdriver.TestRunConfiguration}     config      The test configuration.
             * @param   {Function}                              done        Callback to execute when an individual test / spec has finished.
             * @param   {Function}                              completed   Callback to execute when the entire test case / suite has finished.
             *
             * @return {Boolean}    Boolean indicating if the plugin can handle executing the specified test.
             */
            runTestConfiguration : function(config, done, completed) {
                var handled = config.getTestCaseInfo().getType() === TestType.JASMINE;
                if(handled) {
                    jasmine.getEnv().addReporter(new jasmintd.JSTDReporter(done, completed));
                    jasmine.getEnv().execute();
                }
                return handled;
            },
            /**
             * @private
             * @description
             * Fires after tests have been loaded, prior to tests being executed, providing a chance to build the test
             * run configuration by hand.  In this method we let JSTD know about the Jasmine tests that we're about to run.
             *
             * @param   {Array<jstestdriver.TestCaseInfo>}          info        Array of test case information.
             * @param   {Array<string>}                             filters     Array of test filters.
             * @param   {Array<jstestdriver.TestRunConfiguration>}  testConfigs The Array of test configurations to populate.
             *
             * @return  {void}
             */
            getTestRunsConfigurationFor : function(info, filters, testConfigs) {
                var i,
                    ii,
                    j,
                    jj,
                    suites      = jasmine.getEnv().currentRunner().suites(),
                    all         = filters.indexOf('all') !== -1,
                    specNames,
                    specs;
                if(!all) {
                    jasmine.getEnv().specFilter = function(spec) {
                        var suite   =   (spec instanceof jasmine.Spec ? spec.suite : spec),
                            included = filters.indexOf(suite.getFullName(true)) !== -1;
                        if(!included) {
                            do {
                                included = filters.indexOf(suite.description) !== -1;
                            } while((suite = suite.parentSuite) && !included);
                        }
                        return included;
                    };
                }
                for(i = 0, ii = suites.length; i < ii; i++) {
                    if(all || jasmine.getEnv().specFilter(suites[i])) {
                        specs       = suites[i].specs();
                        specNames   = [];
                        for(j = 0, jj = specs.length; j < jj; j++) {
                            specNames.push(specs[j].description);
                        }
                        testConfigs.push(
                            new jstestdriver.TestRunConfiguration(
                                new jstestdriver.TestCaseInfo(
                                    suites[i].getFullName(true),
                                    TestCase(suites[i].getFullName(true)),
                                    TestType.JASMINE
                                ),
                                specNames
                            )
                        );
                    }
                }
            }
        }
    );

}());