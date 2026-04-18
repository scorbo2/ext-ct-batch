package ca.corbett.crypttext.extensions.batch;

import ca.corbett.crypttext.crypt.CryptUtil;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.io.TextFileDetector;
import ca.corbett.extras.progress.SimpleProgressWorker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A worker thread that will perform a batch encrypt/decrypt operation on a given directory,
 * with optional recursion. Note that by default, only text files (with a ".txt" extension) are considered.
 * If you wish to consider other extensions (markdown files, json, xml, whatever), you must invoke
 * setExtensions() before processing begins.
 * <p>
 * <b>How to use</b>
 * </p>
 * <ol>
 *     <li>Supply an Operation (encrypt or decrypt), a directory, and whether to recurse into subdirectories.</li>
 *     <li>You must supply the password to use for encryption/decryption. This will apply to ALL files.</li>
 *     <li>Supply this thread to a MultiProgressDialog to get automatic progress reporting and a cancel button.</li>
 *     <li>Add a SimpleProgressListener to listen for completion or canceled events.</li>
 *     <li>On progressComplete, you can query for the results (total processed, number skipped/errored/succeeded)</li>
 *     <li>On progressCanceled, you can still query for the results that were processed up until the cancellation.</li>
 * </ol>
 * <p>
 * By default, failures are logged at WARNING level, skipped files are logged at FINE level, and successful
 * operations are not logged. You can change this behavior by invoking setLogLevels() before processing begins.
 * </p>
 * <p>
 * Instances of this SimpleProgressWorker are safe to re-use, after completion, if you wish to perform the same
 * operation on the same directory again. To switch directories, or to perform a different operation,
 * create a new instance. It is not safe to start a second run with the same instance while the first
 * run is still in progress.
 * </p>
 * <p>
 *     <b>IMPORTANT NOTE:</b> The callbacks (onProgress, onCompleted, etc.) within this class are invoked
 *     from the worker thread, NOT the Swing EDT. If you wish to do UI updates in response to these callbacks,
 *     you MUST marshal those calls to the EDT using SwingUtilities.invokeLater() or similar.
 *     Failure to do so may result in deadlocks or other threading issues, as Swing itself is not thread-safe.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class BatchThread extends SimpleProgressWorker {

    private static final Logger log = Logger.getLogger(BatchThread.class.getName());

    public enum Operation {
        ENCRYPT, DECRYPT
    }

    private final File directory;
    private final String password;
    private final boolean recursive;
    private final Operation operation;
    private final List<String> extensions;
    private volatile Level erroredLevel;
    private volatile Level skippedLevel;
    private volatile Level succeededLevel;

    private final AtomicInteger totalProcessed;
    private final AtomicInteger skipped;
    private final AtomicInteger errored;
    private final AtomicInteger succeeded;
    private volatile boolean wasCancelled;

    public BatchThread(Operation operation, String password, File dir, boolean recursive) {
        if (operation == null) {
            throw new IllegalArgumentException("operation cannot be null");
        }
        if (dir == null) {
            throw new IllegalArgumentException("dir cannot be null");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password cannot be null or empty");
        }
        this.operation = operation;
        this.directory = dir;
        this.password = password;
        this.recursive = recursive;
        this.extensions = new ArrayList<>();
        extensions.add("txt"); // by default, we only look for text files
        this.erroredLevel = Level.WARNING;
        this.skippedLevel = Level.FINE;
        this.succeededLevel = null; // by default, we don't log successes at all
        this.totalProcessed = new AtomicInteger(0);
        this.skipped = new AtomicInteger(0);
        this.errored = new AtomicInteger(0);
        this.succeeded = new AtomicInteger(0);
    }

    public int getTotalProcessed() {
        return totalProcessed.get();
    }

    public int getSkipped() {
        return skipped.get();
    }

    public int getErrored() {
        return errored.get();
    }

    public int getSucceeded() {
        return succeeded.get();
    }

    public boolean wasCancelled() {
        return wasCancelled;
    }

    public List<String> getExtensions() {
        return new ArrayList<>(extensions);
    }

    /**
     * Must be invoked before processing begins! This allows you to search for other
     * file extensions (by default, we only look for text files with ".txt" extensions).
     * Your supplied list does not need to include the dot; for example, to search for
     * markdown files: setExtensions(List.of("md")) is fine.
     * <p>
     * If your list is null or empty, we will search for ALL files, regardless of extension.
     * </p>
     * <p>
     * Invoking this while a run is in progress will have no effect! It will not take
     * effect until the next run.
     * </p>
     *
     * @param extensions A list of file extensions, without the dot character.
     */
    public void setExtensions(List<String> extensions) {
        this.extensions.clear();
        if (extensions != null) {
            this.extensions.addAll(extensions);
        }
    }

    /**
     * Must be invoked before processing begins! This allows you to change our logging
     * behavior. By default, failures are logged at WARNING level, skipped files are logged at FINE level,
     * and successful operations are not logged. You can change this behavior here.
     * To disable logging for a particular event, pass null for that event's level.
     * <p>
     * It is safe to invoke this even if a run is in progress. Changes will take effect
     * immediately, and will apply to all subsequent log messages.
     * </p>
     *
     * @param erroredLevel   the log level to log failures at, or null to not log failures
     * @param skippedLevel   the log level to log skipped files at, or null to not log skipped files
     * @param succeededLevel the log level to log successful operations at, or null to not log successes
     */
    public void setLogLevels(Level erroredLevel, Level skippedLevel, Level succeededLevel) {
        this.erroredLevel = erroredLevel;
        this.skippedLevel = skippedLevel;
        this.succeededLevel = succeededLevel;
    }

    @Override
    public void run() {
        List<String> extensions = getExtensions(); // make a copy to prevent mutation while we're running
        totalProcessed.set(0);
        skipped.set(0);
        errored.set(0);
        succeeded.set(0);
        wasCancelled = false;

        // If we were given garbage input, we're done immediately:
        if (!directory.exists() || !directory.isDirectory() || !directory.canRead()) {
            log.warning("BatchThread: given directory is not a readable directory: " + directory.getAbsolutePath());
            return;
        }

        // We want our progress dialog to appear immediately, but we don't yet know how
        // many files we have to process. Scanning the given directory may be a costly
        // operation, depending on how large it is. So, as a bit of a hacky workaround,
        // we'll fire progressBegins with a dummy total of 1, just to get the progress bar up:
        fireProgressBegins(1);

        // Scan our directory:
        List<File> files = FileSystemUtil.findFiles(directory, recursive, extensions);

        // Now we can properly set the bounds of our progress bar:
        fireProgressBegins(files.size()); // A bit wonky to fire "begins" twice, but it is what it is.

        final String verb = operation == Operation.ENCRYPT ? "Encrypting" : "Decrypting";
        final String pastTenseVerb = operation == Operation.ENCRYPT ? "encrypted" : "decrypted";
        int currentStep = 0;
        try {
            for (File file : files) {
                boolean shouldContinue = fireProgressUpdate(currentStep, verb + ": " + file.getAbsolutePath());
                if (!shouldContinue) {
                    wasCancelled = true;
                    break;
                }
                currentStep++;

                try {
                    totalProcessed.incrementAndGet();

                    // Quick sanity check before we continue (just because the extension matches,
                    // doesn't mean this is a file that we can actually process):
                    if (!TextFileDetector.isTextFile(file)) {
                        internalLog(skippedLevel, "Skipping non-text file: " + file.getAbsolutePath());
                        skipped.incrementAndGet();
                        continue;
                    }

                    if (operation == Operation.ENCRYPT) {
                        if (CryptUtil.isCryptTextWrapped(file)) {
                            internalLog(skippedLevel, "Skipping already-encrypted file: " + file.getAbsolutePath());
                            skipped.incrementAndGet();
                            continue;
                        }

                        CryptUtil.encryptInPlace(file, password);
                    }
                    else {
                        if (!CryptUtil.isCryptTextWrapped(file)) {
                            internalLog(skippedLevel, "Skipping already-decrypted file: " + file.getAbsolutePath());
                            skipped.incrementAndGet();
                            continue;
                        }

                        CryptUtil.decryptInPlace(file, password);
                    }

                }
                catch (Exception e) {
                    internalLog(erroredLevel, "Error processing file: " + file.getAbsolutePath(), e);
                    errored.incrementAndGet();
                    continue;
                }

                internalLog(succeededLevel, "Successfully " + pastTenseVerb + " file: " + file.getAbsolutePath());
                succeeded.incrementAndGet();
            }
        }

        // No matter what happens above, we have to fire one of our termination events,
        // otherwise the progress dialog will never close:
        finally {
            if (wasCancelled) {
                fireProgressCanceled();
            }
            else {
                fireProgressComplete();
            }
        }
    }

    /**
     * Shorthand for internalLog(level, message, null).
     */
    private void internalLog(Level level, String message) {
        internalLog(level, message, null);
    }

    /**
     * Invoked internally to do the right thing depending on the given (nullable) Level,
     * and the given message parameters. If the given Level is null, nothing happens.
     *
     * @param level   the log level to log this message at, or null to not log this message
     * @param message the message to log
     * @param e       an optional Throwable to log along with the message; can be null if not applicable
     */
    private void internalLog(Level level, String message, Throwable e) {
        if (level != null) {
            if (e != null) {
                log.log(level, message, e);
            }
            else {
                log.log(level, message);
            }
        }
    }
}
