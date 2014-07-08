package com.sb.elsinore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 *
 *
 */
public class StatusRecorder implements Runnable {

    private JSONObject lastStatus = null;
    private long lastStatusTime = 0;
    private String logFile = null;
    private Thread thread;
    private static final long SLEEP = 1000 * 5; // 5 seconds - is this too fast?
    private long startTime = 0;

    /**
     * Start the thread.
     */
    public  final void start() {
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(this);
            thread.setDaemon(true);
            thread.start();
        }

    }

    /**
     * Stop the thread.
     */
    public final void stop() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    /**
     * Main runnable, updates the files every five seconds.
     */
    @Override
    public final void run() {
        //This will store multiple logs - one for raw data,
        // one for each series (duty & temperature per vessel)
        // For now - we'll store Duty, temperature vs time

        //Assume new logs on each run
        startTime = System.currentTimeMillis();

        String directory = "graph-data/" + startTime + "/";
        File directoryFile = new File(directory);
        directoryFile.mkdirs();

        //Generate a new log file under the current directory
        logFile = directory + "raw.log";

        File file = new File(this.logFile);
        boolean fileExists = file.exists();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        try {
            while (true) {
                //Just going to record when something changes
                String status = LaunchControl.getJSONStatus();
                JSONObject newStatus = (JSONObject) JSONValue.parse(status);

                if (lastStatus == null || isDifferent(lastStatus, newStatus)) {
                    //For now just log the whole status
                    //Eventually we may want multiple logs, etc.
                    writeToLog(newStatus, fileExists);

                    Date now = new Date();

                    if (lastStatus != null
                            && now.getTime() - lastStatusTime > SLEEP) {
                        //Print out a point before now to make sure
                        // the plot lines are correct
                        printJsonToCsv(new Date(now.getTime() - SLEEP),
                                lastStatus, directory);
                    }

                    printJsonToCsv(now, newStatus, directory);
                    lastStatus = newStatus;
                    lastStatusTime = now.getTime();

                    fileExists = true;
                }

                Thread.sleep(SLEEP);

            }
        } catch (InterruptedException ex) {
            BrewServer.LOG.warning("Status Recorder shutting down");
            return;
            //Don't do anything, this is how we close this out.
        }

    }

    /**
     * Save the status to the directory.
     * @param now The current date to save the datapoint for.
     * @param newStatus The JSON Status object to dump
     * @param directory The graph data directory.
     */
    protected final void printJsonToCsv(final Date now,
            final JSONObject newStatus, final String directory) {
        //Now look for differences in the temperature and duty
        JSONArray vessels = (JSONArray) newStatus.get("vessels");
        for (int x = 0; x < vessels.size(); x++) {
            JSONObject vessel = (JSONObject) vessels.get(x);
            if (vessel.containsKey("name")) {
                String name = vessel.get("name").toString();

                if (vessel.containsKey("tempprobe")) {
                    String temp = ((JSONObject) vessel.get("tempprobe"))
                            .get("temp").toString();
                    File tempFile = new File(directory + name + "-temp.csv");
                    appendToLog(tempFile, now.getTime() + "," + temp + "\r\n");
                }

                if (vessel.containsKey("pidstatus")) {
                    JSONObject pid = (JSONObject) vessel.get("pidstatus");
                    String duty = "0";
                    if (pid.containsKey("actualduty")) {
                        duty = pid.get("actualduty").toString();
                    } else if (!pid.get("mode").equals("off")) {
                        duty = pid.get("duty").toString();
                    }
                    File dutyFile = new File(directory + name + "-duty.csv");
                    appendToLog(dutyFile, now.getTime() + "," + duty + "\r\n");
                }

            }
        }
    }

    /**
     * Save the string to the log file.
     * @param file The file object to save to
     * @param toAppend The string to add to the file
     */
    protected final void appendToLog(final File file, final String toAppend) {
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(file, true);
            fileWriter.write(toAppend);
        } catch (IOException ex) {
            BrewServer.LOG.warning("Could not save to file: "
                    + file.getAbsolutePath() + file.getName());
        } finally {
            try {
                if (fileWriter != null) {
                    fileWriter.close();
                }
            } catch (IOException ex) {
                BrewServer.LOG.warning("Could not close filewriter: "
                        + file.getAbsolutePath() + file.getName());
            }
        }
    }

    /**
     * Write a JSON object to the log file.
     * @param status The JSON Object to log
     * @param fileExists If the file exists, prepend a ","
     * otherwise an open brace "["
     */
    protected final void writeToLog(final JSONObject status,
            final boolean fileExists) {
        String append = fileExists ? "," : "[" + status.toJSONString();
        appendToLog(new File(this.logFile), append);
    }

    /**
     * Check to see if the objects are different.
     * @param previous The first object to check.
     * @param current The second object to check
     * @return True if the objects are different
     */
    protected final boolean isDifferent(final JSONObject previous,
            final JSONObject current) {
        if (previous.size() != current.size()) {
            return true;
        }

        for (Iterator<Object> it = previous.keySet().iterator(); it.hasNext();)
        {
            Object key = it.next();
            if (!"elapsed".equals(key)) {
                Object previousValue = previous.get(key);
                Object currentValue = current.get(key);

                if (compare(previousValue, currentValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check to see if the JSONArrays are different.
     * @param previous The first JSONArray to check
     * @param current The second JSONArray to check.
     * @return True if the JSONArrays are different
     */
    protected final boolean isDifferent(final JSONArray previous,
            final JSONArray current) {

        if (previous.size() != current.size()) {
            return true;
        }

        for (int x = 0; x < previous.size(); x++) {
            Object previousValue = previous.get(x);
            Object currentValue = current.get(x);

            if (compare(previousValue, currentValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compare two generic objects.
     * @param previousValue First object to check
     * @param currentValue Second object to check
     * @return True if the objects are different, false if the same.
     */
    protected final boolean compare(final Object previousValue,
            final Object currentValue) {
        if (previousValue instanceof JSONObject
                && currentValue instanceof JSONObject) {
            if (isDifferent((JSONObject) previousValue,
                    (JSONObject) currentValue)) {
                return true;
            }
        } else if (previousValue instanceof JSONArray
                && currentValue instanceof JSONArray) {
            if (isDifferent((JSONArray) previousValue,
                    (JSONArray) currentValue)) {
                return true;
            }
        } else {
            if (!previousValue.equals(currentValue)) {
                return true;
            }
        }

        return false;
    }
}
