package costco;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main class: asks for the simulation settings, starts the Customer
 * (producer) and Cashier (consumer) threads around one shared CheckoutQueue,
 * and prints a report used for the staffing experiments.
 *
 * Based on ex2.BlockingBufferTest.
 *
 * Suggested experiments (customers = 20 unless noted):
 *   1. Understaffed:  1 cashier,  queue size 5
 *   2. Optimal:       3 cashiers, queue size 5
 *   3. Overstaffed:  10 cashiers, queue size 5
 *   4. Small queue:   3 cashiers, queue size 1
 *   5. Large queue:   3 cashiers, queue size 20
 */
public class CostcoSimulation {
    private static final double CASHIER_WAGE_PER_HOUR = 18.00;

    public static void main(String[] args) throws InterruptedException {
        Scanner input = new Scanner(System.in);

        System.out.println("=== Costco Checkout Simulator ===");
        int numberOfCashiers  = readPositiveInt(input, "Number of cashiers", 3);
        int numberOfCustomers = readPositiveInt(input, "Number of customers", 20);
        int queueCapacity     = readPositiveInt(input, "Checkout queue size", 5);
        int maxShoppingTimeMs = readPositiveInt(input, "Max shopping time per customer (ms)", 3000);
        int maxCheckoutTimeMs = readPositiveInt(input, "Max checkout time per customer (ms)", 1500);
        System.out.println();

        CheckoutQueue checkoutQueue = new CheckoutQueue(queueCapacity);

        // Separate pools so we know when every customer has joined the line
        ExecutorService customerPool = Executors.newCachedThreadPool();
        ExecutorService cashierPool  = Executors.newCachedThreadPool();

        long startTime = System.currentTimeMillis();

        // 1. Open the registers (consumers)
        List<Cashier> cashiers = new ArrayList<>();
        for (int i = 1; i <= numberOfCashiers; i++) {
            Cashier cashier = new Cashier(i, checkoutQueue, maxCheckoutTimeMs);
            cashiers.add(cashier);
            cashierPool.execute(cashier);
        }

        // 2. Let the customers in (producers)
        for (int i = 1; i <= numberOfCustomers; i++) {
            customerPool.execute(new Customer(i, checkoutQueue, maxShoppingTimeMs));
        }

        // 3. Wait until every customer has joined the line
        customerPool.shutdown();
        customerPool.awaitTermination(10, TimeUnit.MINUTES);

        // 4. Closing time: one CLOSE_SIGNAL per cashier (poison pills)
        checkoutQueue.closeLine(numberOfCashiers);

        // 5. Wait for the cashiers to finish the line and go home
        cashierPool.shutdown();
        cashierPool.awaitTermination(10, TimeUnit.MINUTES);

        long totalTimeMs = System.currentTimeMillis() - startTime;

        printReport(numberOfCashiers, numberOfCustomers, checkoutQueue, cashiers, totalTimeMs);
        input.close();
    }

    /** Prompts for a positive number. Pressing Enter uses the default. */
    private static int readPositiveInt(Scanner input, String prompt, int defaultValue) {
        while (true) {
            System.out.printf("%s [default %d]: ", prompt, defaultValue);
            if (!input.hasNextLine()) {
                return defaultValue;
            }
            String line = input.nextLine().trim();
            if (line.isEmpty()) {
                return defaultValue;
            }
            try {
                int value = Integer.parseInt(line);
                if (value > 0) {
                    return value;
                }
            }
            catch (NumberFormatException ignored) {
                // fall through to the error message
            }
            System.out.println("Please enter a whole number greater than 0.");
        }
    }

    private static void printReport(int numberOfCashiers, int numberOfCustomers,
                                    CheckoutQueue queue, List<Cashier> cashiers,
                                    long totalTimeMs) {
        System.out.println();
        System.out.println("=========== SIMULATION REPORT ===========");
        System.out.printf("Cashiers: %d | Customers: %d | Queue size: %d%n",
                numberOfCashiers, numberOfCustomers, queue.getCapacity());
        System.out.printf("Total simulation time:  %,d ms%n", totalTimeMs);
        System.out.printf("Customers served:       %d%n", queue.getCustomersServed());
        System.out.printf("Average wait in line:   %.1f ms%n", queue.getAverageWaitMs());
        System.out.printf("Longest wait in line:   %,d ms%n", queue.getLongestWaitMs());
        System.out.printf("Longest line seen:      %d of %d spots%n",
                queue.getLongestLine(), queue.getCapacity());

        System.out.println();
        System.out.println("Cashier   Served   Busy (ms)   Utilization");
        for (Cashier cashier : cashiers) {
            double utilization = totalTimeMs == 0 ? 0
                    : 100.0 * cashier.getTotalBusyTimeMs() / totalTimeMs;
            System.out.printf("%7d   %6d   %9d   %10.1f%%%n",
                    cashier.getCashierId(), cashier.getCustomersServed(),
                    cashier.getTotalBusyTimeMs(), utilization);
        }

        System.out.println();
        System.out.printf("Labor cost: %d cashiers x $%.2f/hour = $%.2f per hour%n",
                numberOfCashiers, CASHIER_WAGE_PER_HOUR,
                numberOfCashiers * CASHIER_WAGE_PER_HOUR);
        System.out.println("=========================================");
    }
}
