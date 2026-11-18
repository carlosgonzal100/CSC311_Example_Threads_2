package costco;

import java.security.SecureRandom;

/**
 * A Costco cashier: the CONSUMER in the producer-consumer pattern.
 *
 * Each Cashier runs on its own thread. It repeatedly takes the next customer
 * ID from the shared CheckoutQueue and spends a random amount of time
 * checking that customer out. If the line is empty, serveNext blocks (the
 * cashier waits) until a customer arrives.
 *
 * The cashier stops when it takes CLOSE_SIGNAL from the queue. After every
 * customer has joined the line, the main class puts one CLOSE_SIGNAL into
 * the queue for each cashier (a "poison pill"), so every cashier finishes
 * the real customers first and then goes home.
 *
 * Based on ex2.Consumer: checking out replaces the random sleep, and
 * serveNext replaces blockingGet.
 */
public class Cashier implements Runnable {
    /** Put this into the queue once per cashier to tell the cashiers to stop. */
    public static final int CLOSE_SIGNAL = -1;

    private static final SecureRandom generator = new SecureRandom();

    private final int cashierId;               // which register this is
    private final CheckoutQueue checkoutQueue;  // the line shared with every customer
    private final int maxCheckoutTimeMs;        // longest time one checkout can take

    // Stats for the experiments. Only this cashier's thread writes them,
    // and the main class reads them after the thread has finished.
    private int customersServed = 0;
    private long totalBusyTimeMs = 0;

    public Cashier(int cashierId, CheckoutQueue checkoutQueue, int maxCheckoutTimeMs) {
        this.cashierId = cashierId;
        this.checkoutQueue = checkoutQueue;
        this.maxCheckoutTimeMs = maxCheckoutTimeMs;
    }

    @Override
    public void run() {
        try {
            while (true) {
                // 1. Take the next customer (blocks if the line is empty)
                int customerId = checkoutQueue.serveNext();

                // 2. Closing time: stop serving
                if (customerId == CLOSE_SIGNAL) {
                    break;
                }

                // 3. Check the customer out for a random amount of time
                int checkoutTime = generator.nextInt(maxCheckoutTimeMs);
                System.out.printf("Cashier %d is serving customer %2d (%d ms)%n",
                        cashierId, customerId, checkoutTime);
                Thread.sleep(checkoutTime);

                customersServed++;
                totalBusyTimeMs += checkoutTime;
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); // keep the interrupt flag, like ex2 does
        }

        System.out.printf("Cashier %d is closing. Served %d customers, busy for %d ms%n",
                cashierId, customersServed, totalBusyTimeMs);
    }

    public int getCashierId() {
        return cashierId;
    }

    public int getCustomersServed() {
        return customersServed;
    }

    public long getTotalBusyTimeMs() {
        return totalBusyTimeMs;
    }
}
