package xyz.chener.ext.napt.server.utils;

public class Utils {

    public static interface RunnableWithException {
        void run() throws Exception;
    }

    public static void runIgnoreException(RunnableWithException runnable){
        try {
            runnable.run();
        } catch (Exception ignored) { }
    }

}
