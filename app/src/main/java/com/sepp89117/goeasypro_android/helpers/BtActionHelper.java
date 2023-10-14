package com.sepp89117.goeasypro_android.helpers;

import android.util.Log;

import java.util.Date;
import java.util.LinkedList;

public class BtActionHelper {
    private static final String TAG = "BtActionHelper";
    private static final int MAX_FIFO_SIZE = 15;
    private static final int BT_ACTION_DELAY = 125;

    private final LinkedList<Runnable> btActionList = new LinkedList<>();
    private boolean gattInProgress;
    private boolean execShouldStop;
    private Date btActionStart;
    private Thread executionThread;

    public BtActionHelper() {
        this.gattInProgress = false;
        this.execShouldStop = false;
        this.btActionStart = new Date();
    }

    public boolean queueAction(Runnable runnable) {
        synchronized (this.btActionList) {
            if (this.btActionList.size() > MAX_FIFO_SIZE) {
                return false;
            }
            this.btActionList.add(runnable);
        }

        return true;
    }

    public boolean queueActionIfNotQueued(Runnable runnable) {
        synchronized (this.btActionList) {
            return this.btActionList.contains(runnable) || queueAction(runnable);
        }
    }

    public void clearActions() {
        synchronized (this.btActionList) {
            this.btActionList.clear();
        }
    }

    public void startExecution() {
        this.gattInProgress = false;
        this.executionThread = getNewExecThread();
        this.executionThread.start();
    }

    public void resetGattInProgress() {
        this.gattInProgress = false;
    }

    public boolean isGattInProgress() {
        return this.gattInProgress;
    }

    public long getLastExecStartDate() {
        return btActionStart.getTime();
    }

    public boolean isExecutionAlive() {
        return executionThread != null && executionThread.isAlive() && !executionThread.isInterrupted();
    }

    public void stopAndClear() {
        try {
            if (isExecutionAlive()) {
                execShouldStop = true;
                executionThread.join(250);
            }
        } catch (InterruptedException e) {
            if (isExecutionAlive()) {
                Log.e(TAG, "stop execution failed");
            }
        }
    }

    private Thread getNewExecThread() {
        return new Thread(() -> {
            while (!execShouldStop) {
                executeNext();
            }
            clearActions();
        });
    }

    private void executeNext() {
        Runnable toExec;

        if (!this.gattInProgress) {
            this.gattInProgress = true;

            synchronized (this.btActionList) {
                toExec = this.btActionList.pollFirst();
            }

            if (toExec == null) {
                this.gattInProgress = false;
            } else {
                Date now = new Date();
                while (now.getTime() - btActionStart.getTime() < BT_ACTION_DELAY) {
                    try {
                        Thread.sleep(BT_ACTION_DELAY - (now.getTime() - btActionStart.getTime()));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    now = new Date();
                }
                btActionStart = now;
                toExec.run();
            }
        }
    }

    public void release() {
        stopAndClear();
        executionThread = null;
    }
}
