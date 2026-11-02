package ca.yorku.eecs3311.spotter
        .presentation.gui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;

/**
 * Runs slow work off the interface thread.
 *
 * Everything interesting Spotter does is slow by
 * interface standards. A model call takes
 * seconds, an import reads thousands of rows, and
 * JavaFX has one thread that draws. Work done on
 * that thread freezes the window, which on a
 * model call means a frozen window for several
 * seconds, and a user who concludes the
 * application has crashed is not wrong to.
 *
 * So the rule here is absolute: the work runs on
 * a background thread and the result is handed
 * back on the interface thread, where it is safe
 * to touch controls. Callers never see a thread.
 */
public final class FxTaskRunner
        implements AutoCloseable {

    private final ExecutorService workers;

    public FxTaskRunner() {
        this.workers =
            Executors.newSingleThreadExecutor(
                namedDaemon());
    }

    /**
     * One worker, and a daemon one.
     *
     * One, because the work is a person asking
     * for something and waiting: two model calls
     * at once would only make both slower and
     * the order of their results unpredictable.
     * A daemon, so closing the window ends the
     * process rather than leaving it alive
     * behind a thread nobody can see.
     */
    private static ThreadFactory namedDaemon() {
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "spotter-worker");
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Runs work and delivers its result on the
     * interface thread.
     *
     * @param work      what to do in the
     *                  background
     * @param onSuccess given the result, on the
     *                  interface thread
     * @param onFailure given whatever went
     *                  wrong, on the interface
     *                  thread
     * @param <T>       what the work produces
     */
    public <T> void run(
            Supplier<T> work,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure) {

        if (work == null) {
            throw new IllegalArgumentException(
                    "There is no work to run");
        }

        workers.submit(() -> {
            try {
                T result = work.get();
                onInterfaceThread(() ->
                    onSuccess.accept(result));
            } catch (RuntimeException
                    | Error failure) {
                onInterfaceThread(() ->
                    onFailure.accept(failure));
            }
        });
    }

    /**
     * Runs an action on the interface thread,
     * now if that is already where we are.
     *
     * Platform.runLater from the interface thread
     * would still work but would defer the action
     * by a frame, which makes a test of panel
     * behaviour depend on timing. This keeps it
     * immediate where it can be.
     */
    public static void onInterfaceThread(
            Runnable action) {
        if (action == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        Platform.runLater(action);
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }
}
