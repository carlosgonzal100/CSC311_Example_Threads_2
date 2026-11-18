package costco;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The checkout line shared by every Customer and Cashier: the BUFFER in the
 * producer-consumer pattern.
 *
 * It holds customer IDs in an ArrayBlockingQueue, which does all of the
 * synchronization for us:
 *   - put()  blocks when the line is full, so customers wait for room
 *   - take() blocks when the line is empty, so cashiers wait for customers
 * Because ArrayBlockingQueue is thread-safe, two cashiers can never grab the
 * same customer and no customer can be lost (no race conditions).
 *
 * It also records how long each customer waited in line, so the main class
 * can compare staffing levels in the experiments.
 *
 * Based on ex2.BlockingBuffer: joinQueue replaces blockingPut and
 * serveNext replaces blockingGet.
 */
public class CheckoutQueue {
    private final ArrayBlockingQueue<Integer> line; // customer IDs waiting to check out
    private final int capacity;                      // most customers the line can hold

    // When each customer got in line (customer ID -> time in ms).
    // ConcurrentHashMap because customer and cashier threads use it at the same time.
    private final Map<Integer, Long> joinTimes = new ConcurrentHashMap<>();

    // Wait-time stats. synchronized methods below protect these.
    private int customersServed = 0;
    private long totalWaitMs = 0;
    private long longestWaitMs = 0;
    private int longestLine = 0;

    public CheckoutQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Queue capacity must be at least 1");
        }
        this.capacity = capacity;
        this.line = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * A customer gets in line. Blocks while the line is full.
     * Called by Customer.run().
     */
    public void joinQueue(int customerId) throws InterruptedException {
        joinTimes.put(customerId, System.currentTimeMillis());
        line.put(customerId); // waits here if the line is full

        int size = line.size();
        recordLineLength(size);
        System.out.printf("Customer %2d joined the line\tPeople in line: %d/%d%n",
                customerId, size, capacity);
    }

    /**
     * A cashier takes the next customer. Blocks while the line is empty.
     * Returns Cashier.CLOSE_SIGNAL when it is time for the cashier to stop.
     * Called by Cashier.run().
     */
    public int serveNext() throws InterruptedException {
        int customerId = line.take(); // waits here if the line is empty

        if (customerId == Cashier.CLOSE_SIGNAL) {
            return customerId; // not a real customer, no wait time to record
        }

        Long joinedAt = joinTimes.remove(customerId);
        if (joinedAt != null) {
            recordWait(System.currentTimeMillis() - joinedAt);
        }
        System.out.printf("Customer %2d left the line\tPeople in line: %d/%d%n",
                customerId, line.size(), capacity);
        return customerId;
    }

    /**
     * Closing time: puts one CLOSE_SIGNAL in the line for each cashier.
     * Call this only after every customer has joined the line, so the
     * cashiers finish all real customers before they reach the signals.
     */
    public void closeLine(int numberOfCashiers) throws InterruptedException {
        for (int i = 0; i < numberOfCashiers; i++) {
            line.put(Cashier.CLOSE_SIGNAL);
        }
    }

    private synchronized void recordWait(long waitMs) {
        customersServed++;
        totalWaitMs += waitMs;
        if (waitMs > longestWaitMs) {
            longestWaitMs = waitMs;
        }
    }

    private synchronized void recordLineLength(int size) {
        if (size > longestLine) {
            longestLine = size;
        }
    }

    public synchronized double getAverageWaitMs() {
        return customersServed == 0 ? 0 : (double) totalWaitMs / customersServed;
    }

    public synchronized long getLongestWaitMs() {
        return longestWaitMs;
    }

    public synchronized int getCustomersServed() {
        return customersServed;
    }

    public synchronized int getLongestLine() {
        return longestLine;
    }

    public int getCapacity() {
        return capacity;
    }
}
