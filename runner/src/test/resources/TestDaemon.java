/**
 * Waits 10s and then exits with the message "not killed".
 *
 * Registers a shutdown hook: prints "shutdown (stdout)" to stdout and
 * "shutdown (stderr)" to stderr. If the process is killed externally, the
 * message "not killed" will not appear.
 *
 * This file is written in Java, not Scala, so that it won't rely on any jars.
 *
 * To create a jar out of this file:
 *
 *   <tt>javac TestDaemon.java</tt>
 *   <tt>jar cfe TestDaemon.jar TestDaemon TestDaemon*.class</tt>
 *
 * Test it like this (this is how the test suite will invoke it):
 *
 *   <tt>java -jar TestDaemon.jar</tt>
 */
public class TestDaemon {
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("shutdown (stdout)");
                System.err.println("shutdown (stderr)");
            }
        });

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("not killed");
    }
}
