# Costco Checkout Simulator — Producer-Consumer Lab (CSC311)

A multithreaded Costco checkout simulator built from the `ex2` Producer-Consumer example.
Customers (producers) shop and then join a shared checkout line. Cashiers (consumers) take
customers from the line and check them out. The line is an `ArrayBlockingQueue`, which keeps
the threads synchronized and prevents race conditions.

## Team Members & Contributions

| Member | Contributions |
|---|---|
| **Carlos Gonzalez** | Created the shared repository. Wrote `Customer.java` (producer: shops for a random time, then joins the line) and `Cashier.java` (consumer: serves customers until it receives the `CLOSE_SIGNAL` poison pill; tracks customers served and busy time). |
| **Christopher Gonzalez** | Wrote `CheckoutQueue.java` (the shared line: wraps an `ArrayBlockingQueue`, blocks customers when the line is full and cashiers when it is empty, records wait times and the longest line, and sends the close signals) and `CostcoSimulation.java` (main class: asks for the settings, starts the cashier and customer threads, shuts down cleanly, and prints the simulation report). Ran the experiments and wrote the analysis. |

## How It Maps to the Original `ex2` Code

| `ex2` class | Costco class | Role |
|---|---|---|
| `Producer` | `Customer` | Shops for a random time, then calls `joinQueue(id)` (was `blockingPut`) |
| `Consumer` | `Cashier` | Calls `serveNext()` (was `blockingGet`), then checks the customer out for a random time |
| `BlockingBuffer` | `CheckoutQueue` | Shared `ArrayBlockingQueue` of customer IDs |
| `BlockingBufferTest` | `CostcoSimulation` | Creates the shared queue and runs the threads |

**Why synchronization matters:** without it (see `Ex1/UnsynchronizedBuffer`), two cashiers could
grab the same customer, or a customer could be overwritten and lost. `ArrayBlockingQueue`
handles this for us. `put()` makes a customer wait when the line is full, and `take()` makes a
cashier wait when the line is empty, so every customer is served exactly once.

**Shutting down:** after every customer has joined the line, `CostcoSimulation` calls
`closeLine(n)`, which puts one `CLOSE_SIGNAL` (-1) in the line for each cashier. The signals go
in behind the real customers, so every customer is served before the cashiers stop.

## How to Run

1. Open the project in IntelliJ.
2. Run `src/costco/CostcoSimulation.java`.
3. Answer the prompts, or press Enter to use the default shown:
    - Number of cashiers (default 3)
    - Number of customers (default 20)
    - Checkout queue size (default 5)
    - Max shopping time per customer in ms (default 3000)
    - Max checkout time per customer in ms (default 1500)

The program prints each customer and cashier event, then a report with total time, average
and longest wait, longest line, each cashier's utilization, and hourly labor cost.

## Experiments

All experiments used 20 customers, a max shopping time of 3000 ms and a max checkout time of
1500 ms. We ran each experiment 3 times and averaged the results, because shopping and checkout
times are random.

| Experiment | Cashiers | Queue size | Avg total time | Avg wait in line (range) | Avg longest wait | Avg cashier utilization | Labor cost/hr |
|---|---|---|---|---|---|---|---|
| 1. Understaffed | 1 | 5 | 14,967 ms | 5,975 ms (5,781–6,241) | 11,444 ms | 98.3% | \$18 |
| 2. Optimal | 3 | 5 | 6,130 ms | 708 ms (396–1,053) | 1,862 ms | 83.9% | \$54 |
| 3. Overstaffed | 10 | 5 | 3,714 ms | 0 ms (0–0) | 0 ms | 33.7% | \$180 |
| 4. Small queue | 3 | 1 | 6,117 ms | 904 ms (774–997) | 2,235 ms | 89.0% | \$54 |
| 5. Large queue | 3 | 20 | 5,078 ms | 1,094 ms (909–1,378) | 1,870 ms | 87.5% | \$54 |

## Observations

- **Understaffed (1 cashier):** the cashier was busy about 98% of the time and the line was
  full in every run. Customers waited about 6 seconds on average, roughly 8 times the average
  checkout time of 750 ms. It is the cheapest option and the worst for customers.
- **Optimal (3 cashiers):** waits dropped by about 88% compared with 1 cashier, to about the
  length of one checkout. Cashiers were busy about 84% of the time, so there was little idle
  time. Waits varied the most here (396–1,053 ms), because when cashiers are busy most of the
  time, a few slow checkouts in a row can quickly build a line.
- **Overstaffed (10 cashiers):** no customer waited in any run, but cashiers were idle about
  two thirds of the time. It cost 3.3 times as much as 3 cashiers and finished only about
  1.7 times faster. Labor works out to \$9.00 per customer, compared with \$2.70 with 3 cashiers.
- **Small queue (3 cashiers, size 1):** the average wait was about the same as with the other
  3-cashier setups. A small line does not reduce waiting. It moves the waiting from the line
  into the aisles, because `put()` blocks customers until a spot opens. Cashiers stayed busy
  (89%) because a customer was always ready.
- **Large queue (3 cashiers, size 20):** the line never held more than 11 customers, so the
  extra space went unused. It finished about 1 second faster than the size-1 queue, because
  customers were not blocked from joining the line.
- **Wait time vs. idle cashiers:**

  | Experiment | Cashiers | Avg idle time per cashier | Avg wait in line | Labor cost/hr |
    |---|---|---|---|---|
  | Understaffed | 1 | 1.7% | 5,975 ms | \$18 |
  | Large queue | 3 | 12.5% | 1,094 ms | \$54 |
  | Small queue | 3 | 11.0% | 904 ms | \$54 |
  | Optimal | 3 | 16.1% | 708 ms | \$54 |
  | Overstaffed | 10 | 66.3% | 0 ms | \$180 |

  When cashiers are almost never idle, customers wait a long time. When cashiers are idle most
  of the time, customers never wait, but the store pays for unused labor. About 10–20% idle time
  gives short waits without wasting money.
- **Key takeaway:** the number of cashiers decides how long customers wait. The queue size only
  decides where they wait, as long as it is big enough for the busiest moment.

## Staffing Recommendation: 60 customers/hour at \$18/hour per cashier

The simulation shows that the best balance is when cashiers are busy about **75–90%** of the
time. At about 98% (1 cashier), waits grow out of control. At about 34% (10 cashiers), most
labor is wasted.

### Simulating one hour at 60 customers/hour

To test this directly, we scaled the simulation so that **100 ms = 1 real minute** and
simulated one hour of customers:

- Customers: **60**
- Max shopping time: **6000 ms**, which spreads arrivals over about 60 "minutes" (about 1 customer per minute)
- Max checkout time: **500 ms**, an average of 250 ms, which stands for a **2.5-minute** checkout
- Queue size: **5**

| Cashiers | Labor cost/hr | Avg wait | Longest wait | Time to serve the hour's 60 customers | Cashier utilization |
|---|---|---|---|---|---|
| 2 | \$36 | 7.8 min | 18.4 min | 83 min (23 min behind) | 97% |
| 3 | \$54 | 1.2 min | 4.6 min | 64 min | 76% |
| 4 | \$72 | 0.2 min | 2.5 min | 65 min | 52% |

- **2 cashiers (\$36/hr) cannot keep up.** They were busy 97% of the time, the line was full, the
  average wait was almost 8 minutes, and the last customers were served 23 minutes after the
  hour ended. In a real store, new customers keep arriving, so this backlog would grow every
  hour.
- **3 cashiers (\$54/hr) keep up.** The average wait was about 1 minute, cashiers were busy about
  76% of the time (inside the 75–90% range), and the hour's customers were served almost on time.
- **4 cashiers (\$72/hr)** cut the average wait by only about 1 minute and did not finish any
  sooner. Cashiers were idle about half the time, so the extra \$18/hour buys very little.

This matches the workload math: 60 customers × 2.5 minutes ÷ 60 minutes = 2.5 cashiers kept
fully busy. Two cashiers is less than that, and three leaves a small cushion.

**Recommendation: 3 cashiers (\$54/hour).** This is the cheapest staffing that keeps up with
60 customers/hour and keeps waits around a minute. If real checkouts average closer to 3 minutes,
the workload rises to 3 cashiers fully busy, and the store should staff **4 cashiers (\$72/hour)**
instead.

## Project Structure

```
src/
├── costco/     ← Lab solution (Customer, Cashier, CheckoutQueue, CostcoSimulation)
├── Ex1/        ← Unsynchronized buffer (shows the race condition)
├── ex2/        ← ArrayBlockingQueue version (the base for this lab)
├── ex3/        ← synchronized buffer using wait()/notifyAll()
└── ex4/        ← Circular buffer
```
