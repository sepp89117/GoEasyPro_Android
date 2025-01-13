package com.sepp89117.goeasypro_android.helpers;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * GattOperationQueue is responsible for managing GATT operations in a sequential manner.
 * It ensures only one GATT operation is processed at a time, with optional delays and timeouts.
 */
public class GattOperationQueue {
    private final static String TAG = "GattOperationQueue";
    // Queue to hold GATT operations
    private final Queue<Runnable> operationQueue = new LinkedList<>();
    // Maximum number of operations that can be queued
    private int maxQueueSize = Integer.MAX_VALUE;
    // Flag indicating if an operation is currently being processed
    private boolean isProcessing = false;
    // Delay between successive operations (in milliseconds)
    private long delayBetweenOperationsMs = 0; // Default: no delay
    // Timeout for each operation (in milliseconds)
    private long operationTimeoutMs = 10000; // Default: 10 seconds
    // Handler to manage execution of tasks on the main thread
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Task for the watchdog mechanism to monitor operation timeouts
    private Runnable watchdogTask;

    /**
     * Sets the delay between operations.
     * If set to 0, operations will run back-to-back without delay.
     *
     * @param delayMs Delay between operations in milliseconds.
     */
    public void setDelayBetweenOperations(long delayMs) {
        this.delayBetweenOperationsMs = delayMs;
    }

    /**
     * Sets the timeout for each operation.
     * If the timeout is exceeded, the operation is considered failed, and processing continues.
     *
     * @param timeoutMs Timeout for each operation in milliseconds.
     *                  Set to 0 to disable the timeout mechanism.
     */
    public void setOperationTimeout(long timeoutMs) {
        this.operationTimeoutMs = timeoutMs;
    }

    /**
     * Sets the maximum size of the queue.
     * Prevents adding new operations if the queue exceeds this size.
     *
     * @param size Maximum queue size.
     */
    public void setMaxQueueSize(int size) {
        this.maxQueueSize = size;
    }

    /**
     * Adds a new GATT operation to the queue.
     * If the queue size exceeds the maximum, the operation is rejected.
     *
     * @param operation The GATT operation to queue as a Runnable.
     * @return True if the operation was successfully added, false if the queue was full.
     */
    public synchronized boolean addOperation(Runnable operation) {
        if (operationQueue.size() >= maxQueueSize) {
            Log.e(TAG, "The operation could not be added because the queue was full!");
            return false;
        }
        operationQueue.add(operation);
        processNext();
        return true;
    }

    private boolean isPaused = false;
    public synchronized void pauseProcessing() {
        isPaused = true;
        while (isProcessing);
        isProcessing = true;
        startWatchdog();
    }

    /**
     * Processes the next operation in the queue, if available.
     * Starts a watchdog timer to monitor operation timeout and executes the operation.
     */
    private synchronized void processNext() {
        if (isProcessing || isPaused || operationQueue.isEmpty()) {
            return;
        }

        isProcessing = true;
        Runnable operation = operationQueue.poll();

        if (operation != null) {
            startWatchdog();
            handler.post(operation);
        }
    }

    /**
     * Marks the current operation as completed and schedules the next operation.
     * If a delay is configured, the delay is applied before processing the next operation.
     */
    public synchronized void onOperationCompleted() {
        resetWatchdog();
        isProcessing = false;
        isPaused = false;

        if (!operationQueue.isEmpty() && delayBetweenOperationsMs > 0) {
            handler.postDelayed(this::processNext, delayBetweenOperationsMs);
        } else {
            processNext();
        }
    }

    /**
     * Starts the watchdog timer to ensure no operation exceeds the configured timeout.
     * If the timeout is exceeded, the current operation is marked as completed.
     */
    private void startWatchdog() {
        resetWatchdog();
        watchdogTask = () -> {
            if (!operationQueue.isEmpty()) {
                Log.e(TAG, String.format("Operation timeout exceeded with queue size of %s", operationQueue.size()));
            }
            onOperationCompleted();
        };

        if (operationTimeoutMs > 0) {
            handler.postDelayed(watchdogTask, operationTimeoutMs);
        }
    }

    /**
     * Resets the watchdog timer, stopping it if it is running.
     */
    private void resetWatchdog() {
        if (watchdogTask != null) {
            handler.removeCallbacks(watchdogTask);
        }
    }

    /**
     * Resets the queue by clearing all pending operations and stopping execution.
     */
    public void reset() {
        handler.removeCallbacksAndMessages(null);
        operationQueue.clear();
        isProcessing = false;
    }

    /**
     * GattHelper simplifies the usage of the GattOperationQueue to manage BluetoothGatt operations.
     * Provides methods for reading, writing, and managing GATT characteristics and descriptors.
     */
    public static class GattHelper {
        private final static String TAG = "GattHelper";
        private final GattOperationQueue queue;
        private final BluetoothGatt gatt;

        /**
         * Creates a GattHelper with default settings.
         *
         * @param gatt The BluetoothGatt instance for GATT operations.
         */
        public GattHelper(BluetoothGatt gatt) {
            this.queue = new GattOperationQueue();
            this.gatt = gatt;
        }

        /**
         * Creates a new GattHelper instance.
         *
         * @param gatt               The BluetoothGatt instance for performing GATT operations.
         * @param operationTimeoutMs The timeout in milliseconds for each operation.
         */
        public GattHelper(BluetoothGatt gatt, long operationTimeoutMs) {
            this.queue = new GattOperationQueue();
            this.queue.setOperationTimeout(operationTimeoutMs);
            this.gatt = gatt;
        }

        /**
         * Creates a new GattHelper instance.
         *
         * @param gatt                     The BluetoothGatt instance for performing GATT operations.
         * @param delayBetweenOperationsMs The delay in milliseconds between operations.
         * @param operationTimeoutMs       The timeout in milliseconds for each operation.
         */
        public GattHelper(BluetoothGatt gatt, long delayBetweenOperationsMs, long operationTimeoutMs) {
            this.queue = new GattOperationQueue();
            this.queue.setDelayBetweenOperations(delayBetweenOperationsMs);
            this.queue.setOperationTimeout(operationTimeoutMs);
            this.gatt = gatt;
        }

        /**
         * Creates a new GattHelper instance.
         *
         * @param gatt                     The BluetoothGatt instance for performing GATT operations.
         * @param delayBetweenOperationsMs The delay in milliseconds between operations.
         * @param operationTimeoutMs       The timeout in milliseconds for each operation.
         * @param maxQueueSize             The maximum size of the queue.
         */
        public GattHelper(BluetoothGatt gatt, long delayBetweenOperationsMs, long operationTimeoutMs, int maxQueueSize) {
            this.queue = new GattOperationQueue();
            this.queue.setDelayBetweenOperations(delayBetweenOperationsMs);
            this.queue.setOperationTimeout(operationTimeoutMs);
            this.queue.setMaxQueueSize(maxQueueSize);
            this.gatt = gatt;
        }

        /**
         * Queues a read operation for a GATT characteristic.
         *
         * @param characteristic The characteristic to read.
         * @param onProcessed A callback that receives the result of the operation (true if successful, false otherwise).
         * @return True if the operation was successfully queued, false otherwise.
         */
        @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
        public boolean readCharacteristic(BluetoothGattCharacteristic characteristic, Consumer<Boolean> onProcessed) {
            if (characteristic == null) {
                if (onProcessed != null) {
                    onProcessed.accept(false);
                }
                return false;
            }

            return queue.addOperation(() -> {
                boolean success = gatt.readCharacteristic(characteristic);
                if (onProcessed != null) {
                    onProcessed.accept(success);
                }
                if (!success) {
                    Log.e(TAG, String.format("Failed to read characteristic '%s'", characteristic.getUuid()));
                    queue.onOperationCompleted();
                }
            });
        }

        /**
         * Queues a write operation for a GATT characteristic.
         *
         * @param characteristic The characteristic to write to.
         * @param value          The value to write.
         * @param onProcessed A callback that receives the result of the operation (true if successful, false otherwise).
         * @return True if the operation was successfully queued, false otherwise.
         */
        @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
        public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value, Consumer<Boolean> onProcessed) {
            if (characteristic == null) {
                if (onProcessed != null) {
                    onProcessed.accept(false);
                }
                return false;
            }

            return queue.addOperation(() -> {
                characteristic.setValue(value);
                boolean success = gatt.writeCharacteristic(characteristic);
                if (onProcessed != null) {
                    onProcessed.accept(success);
                }
                if (!success) {
                    Log.e(TAG, String.format("Failed to write characteristic '%s'", characteristic.getUuid()));
                    queue.onOperationCompleted();
                }
            });
        }

        /**
         * Queues a read operation for a GATT descriptor.
         *
         * @param descriptor The descriptor to read.
         * @param onProcessed A callback that receives the result of the operation (true if successful, false otherwise).
         * @return True if the operation was successfully queued, false otherwise.
         */
        @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
        public boolean readDescriptor(BluetoothGattDescriptor descriptor, Consumer<Boolean> onProcessed) {
            if (descriptor == null) {
                if (onProcessed != null) {
                    onProcessed.accept(false);
                }
                return false;
            }

            return queue.addOperation(() -> {
                boolean success = gatt.readDescriptor(descriptor);
                if (onProcessed != null) {
                    onProcessed.accept(success);
                }
                if (!success) {
                    Log.e(TAG, String.format("Failed to read descriptor '%s'", descriptor.getUuid()));
                    queue.onOperationCompleted();
                }
            });
        }

        /**
         * Queues a write operation for a GATT descriptor.
         *
         * @param descriptor The descriptor to write to.
         * @param value      The value to write.
         * @param onProcessed A callback that receives the result of the operation (true if successful, false otherwise).
         * @return True if the operation was successfully queued, false otherwise.
         */
        @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
        public boolean writeDescriptor(BluetoothGattDescriptor descriptor, byte[] value, Consumer<Boolean> onProcessed) {
            if (descriptor == null) {
                if (onProcessed != null) {
                    onProcessed.accept(false);
                }
                return false;
            }

            return queue.addOperation(() -> {
                descriptor.setValue(value);
                boolean success = gatt.writeDescriptor(descriptor);
                if (onProcessed != null) {
                    onProcessed.accept(success);
                }
                if (!success) {
                    Log.e(TAG, String.format("Failed to write descriptor '%s'", descriptor.getUuid()));
                    queue.onOperationCompleted();
                }
            });
        }

        /**
         * Queues an operation to enable or disable notifications for a characteristic.
         *
         * @param characteristic The characteristic to enable/disable notifications for.
         * @param enable         True to enable notifications, false to disable.
         * @param onProcessed A callback that receives the result of the operation (true if successful, false otherwise).
         * @return True if the operation was successfully queued, false otherwise.
         */
        @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
        public boolean setNotification(BluetoothGattCharacteristic characteristic, boolean enable, Consumer<Boolean> onProcessed) {
            if (characteristic == null) {
                if (onProcessed != null) {
                    onProcessed.accept(false);
                }
                return false;
            }

            return queue.addOperation(() -> {
                boolean success = gatt.setCharacteristicNotification(characteristic, enable);
                if (onProcessed != null) {
                    onProcessed.accept(success);
                }
                if (!success) {
                    Log.e(TAG, String.format("Failed to set notification for characteristic '%s'", characteristic.getUuid()));
                }
                queue.onOperationCompleted();
            });
        }

        /**
         * Queues an operation to read the remote RSSI (Received Signal Strength Indicator).
         * This operation retrieves the RSSI value of the connected device.
         *
         * @return True if the operation was successfully queued, false otherwise.
         */
        @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
        public boolean readRemoteRssi() {
            return queue.addOperation(() -> {
                boolean success = gatt.readRemoteRssi();
                if (!success) {
                    Log.e(TAG, "Failed to read remote RSSI");
                    queue.onOperationCompleted();
                }
            });
        }

        /**
         * Queues a disconnect operation to terminate the connection with the remote device.
         *
         * @return True if the operation was successfully queued, false otherwise.
         */
        @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
        public boolean disconnect() {
            boolean success = queue.addOperation(gatt::disconnect);
            if (!success) {
                Log.e(TAG, "Failed to disconnect");
                queue.onOperationCompleted();
            }
            return success;
        }

        /**
         * Pauses processing of operations in the queue until timeout.
         */
        public void pauseProcessing() {
            queue.pauseProcessing();
        }

        /**
         * Signals the queue to process the next operation.
         * This method is typically used to manually trigger processing
         * after a custom operation or external event.
         */
        public void processNext() {
            // Manually complete the current operation and trigger the next.
            queue.onOperationCompleted();
        }

        /**
         * Stops all operations, clears the queue, and resets the internal processing state.
         * Use this method to completely halt and reset the operation queue.
         */
        public void reset() {
            queue.reset();
        }
    }
}

