import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Map;

/**
 * Prints the env, args and JVM args, writes to stderr, waits 10ms, and exits.
 *
 * These behaviors help test that TestApp is invoked correctly and that its
 * parent handles its output streams and exit gracefully.
 *
 * The output to stdout has env lines like: <tt>ENV: VAR=value</tt>.
 *
 * The output to stdout has vmargs lines like: <tt>VMARG: -Xmx256m</tt>.
 *
 * The output to stdout has args lines like: <tt>ARG: -foo</tt>.
 *
 * The output to stdout has "This is on stdout".
 *
 * The output to stderr is "This is on stderr".
 *
 * The exit code is 4: it's chosen randomly to _not_ be 0, so we can test the
 * parent is reading it correctly.
 *
 * This file is written in Java, not Scala, so that it won't rely on any jars.
 *
 * To create a jar out of this file:
 *
 *   <tt>javac TestApp.java</tt>
 *   <tt>jar cfe TestApp.jar TestApp TestApp.class</tt>
 *
 * Test it like this (this is how the test suite will invoke it):
 *
 *   <tt>VAR=VAL java -Xmx256m -jar TestApp.jar arg1 arg2</tt>
 */
public class TestApp {
    public static void main(String[] args) {
        printEnv();
        printVmArgs();
        printArgs(args);

        System.out.println("This is on stdout");
        System.err.println("This is on stderr");

        System.exit(4);
    }

    private static void printEnv() {
        Map<String,String> env = System.getenv();
        for (String var : env.keySet()) {
            System.out.format("ENV: %s=%s\n", var, env.get(var));
        }
    }

    private static void printVmArgs() {
        RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        List<String> vmArgs = bean.getInputArguments();

        for (String vmArg : vmArgs) {
            System.out.println("VMARG: " + vmArg);
        }
    }

    private static void printArgs(String[] args) {
        for (String arg : args) {
            System.out.println("ARG: " + arg);
        }
    }
}
