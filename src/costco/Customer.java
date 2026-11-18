package costco;

import java.security.SecureRandom;

/**
 * A Costco customer: the PRODUCER in the producer-consumer pattern.
 *
 * Each Customer runs on its own thread. It spends a random amount of time
 * shopping, then joins the shared checkout line by putting its ID into the
 * CheckoutQueue. If the line is full, joinQueue blocks (the customer waits)
 * until a cashier frees up a spot.
 *
 * Based on ex2.Producer: shopping replaces the random sleep, and joining the
 * line replaces blockingPut.
 */
public class Customer implements Runnable {
    private static final SecureRandom generator = new SecureRandom();

    private final int customerId;             // which customer this is
    private final CheckoutQueue checkoutQueue; // the line shared with every cashier
    private final int maxShoppingTimeMs;       // longest time a customer can shop

    public Customer(int customerId, CheckoutQueue checkoutQueue, int maxShoppingTimeMs) {
        this.customerId = customerId;
        this.checkoutQueue = checkoutQueue;
        this.maxShoppingTimeMs = maxShoppingTimeMs;
    }

    @Override
    public void run() {
        try {
            // 1. Shop for a random amount of time
            int shoppingTime = generator.nextInt(maxShoppingTimeMs);
            System.out.printf("Customer %2d is shopping (%d ms)%n", customerId, shoppingTime);
            Thread.sleep(shoppingTime);

            // 2. Join the checkout line (blocks if the line is full)
            System.out.printf("Customer %2d is done shopping and heads to checkout%n", customerId);
            checkoutQueue.joinQueue(customerId);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); // keep the interrupt flag, like ex2 does
        }
    }

    public int getCustomerId() {
        return customerId;
    }
}
