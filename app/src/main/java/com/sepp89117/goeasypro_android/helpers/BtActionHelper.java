package com.sepp89117.goeasypro_android.helpers;

import android.util.Log;

import java.util.LinkedList;

public class BtActionHelper {
    private static final String TAG = "BtActionHelper";
    private static final int MAX_FIFO_SIZE = 15;
    private static final int BT_ACTION_DELAY = 125;

    private final LinkedList<Runnable> btActionList = new LinkedList<>();
    private boolean gattInProgress;
    private boolean execShouldStop;
    private long btActionStart;
    private Thread executionThread;

    public BtActionHelper() {
        this.gattInProgress = false;
        this.execShouldStop = false;
        this.btActionStart = System.currentTimeMillis();
    }

    public void queueAction(Runnable runnable) {
        synchronized (this.btActionList) {
            if (this.btActionList.size() > MAX_FIFO_SIZE) {
                return;
            }
            this.btActionList.add(runnable);
        }

    }

    public void queueActionIfNotQueued(Runnable runnable) {
        synchronized (this.btActionList) {
            if (!this.btActionList.contains(runnable)) {
                queueAction(runnable);
            }
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

    public long getLastExecMillis() {
        return btActionStart;
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

        if (!this.gattInProgress && this.btActionList.size() > 0) {
            this.gattInProgress = true;

            synchronized (this.btActionList) {
                toExec = this.btActionList.pollFirst();
            }

            if (toExec == null) {
                this.gattInProgress = false;
            } else {
                while (System.currentTimeMillis() - btActionStart < BT_ACTION_DELAY) {
                    try {
                        Thread.sleep(BT_ACTION_DELAY - (System.currentTimeMillis() - btActionStart));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                btActionStart = System.currentTimeMillis();
                toExec.run();
            }
        } else {
            try {
                Thread.sleep(BT_ACTION_DELAY);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void release() {
        stopAndClear();
        executionThread = null;
    }
}
