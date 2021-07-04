package com.shing.Runnables;

import com.shing.ConsoleColors.ConsoleColors;
import com.shing.Customer;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WARNING SUPPRESSOR
 *  Explanation: There are no mistakes with the code, just that some IDEs like IDEA IntelliJ warns about un-queried LinkedBlockingDequeues and if statements with empty bodies.
 */
@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "StatementWithEmptyBody"})


public class Terminal implements Runnable {
    /////////////////////////////////////////////////////////////////////////////////////// CLASS FIELDS /////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
     * Queues
     * */
    //ArrayBlockingQueues
    //FOYER area with fairness enabled to prevent thread starvation.
    //A bound size of 40 is set as there might be a chance where the foyer has to store extra 5 customers due to the ticket machine breaking instantly at the start of the simulation.
    //40 from Foyer + 5 from Ticket Booth A's queue + 5 from Ticket Booth B's queue = 50 Terminal Max Capacity.
    //IF the ticket machine doesn't break... then 35 from Foyer + 5 from Ticket Machine's queue + 5 from Ticket Booth A's queue + 5 from Ticket Booth B's queue = 50 Terminal Max Capacity.
    private final ArrayBlockingQueue<Customer> foyer = new ArrayBlockingQueue<>(40, true);
    //Ticket Machine's queueing area with fairness enabled to prevent thread starvation.
    //A bound size of 5 (as per stated in the assignment question).
    private final ArrayBlockingQueue<Customer> ticketMachineQ = new ArrayBlockingQueue<>(5, true);
    //Ticket Booth A's queueing area with fairness enabled to prevent thread starvation.
    //A bound size of 5 (as per stated in the assignment question).
    private final ArrayBlockingQueue<Customer> ticketBoothAQ = new ArrayBlockingQueue<>(5, true);
    //Ticket Booth B's queueing area with fairness enabled to prevent thread starvation.
    //A bound size of 5 (as per stated in the assignment question).
    private final ArrayBlockingQueue<Customer> ticketBoothBQ = new ArrayBlockingQueue<>(5, true);
    //LinkedBlockingDequeues
    //A linked blocking dequeue is used here as it has no size limit, and presumed to be always available (as per stated in the assignment question).
    //Also, it provides us the flexibility to use putFirst(), where we can put the objects at the head instead of the tail to better simulate a waiting area.
    //Waiting Area 1 with no bound size.
    private final LinkedBlockingDeque<Customer> waitingArea1 = new LinkedBlockingDeque<>();
    //Waiting Area 2 with no bound size.
    private final LinkedBlockingDeque<Customer> waitingArea2 = new LinkedBlockingDeque<>();
    //Waiting Area 3 with no bound size.
    private final LinkedBlockingDeque<Customer> waitingArea3 = new LinkedBlockingDeque<>();


    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
     * Flags
     * */
    //Running flag for the entire simulation.
    private final AtomicBoolean running = new AtomicBoolean(true);
    //Ticketing Areas.
    private final AtomicBoolean ticketMachineRunning = new AtomicBoolean(true);
    private final AtomicBoolean ticketBoothARunning = new AtomicBoolean(true);
    private final AtomicBoolean ticketBoothBRunning = new AtomicBoolean(true);
    //Queues.
    private final AtomicBoolean ticketMachineQOpen = new AtomicBoolean(true);
    private final AtomicBoolean ticketBoothAQOpen = new AtomicBoolean(true);
    private final AtomicBoolean ticketBoothBQOpen = new AtomicBoolean(true);
    //Customer objects created counter.
    private final AtomicInteger customerCount = new AtomicInteger(1);

    /**
     * Simulation Variables
     *
     * Note: CHANGE HERE TO FORCE ENVIRONMENTS!!!
     */
    //Sets the duration it takes for the ticket machine to issue a ticket (4 seconds as required in the assignment).
    volatile Integer ticketMachineIssueDuration = 4000;
    //Sets the duration it takes for a ticket booth to issue a ticket (8 seconds as required in the assignment).
    volatile Integer ticketBoothIssueDuration = 8000;

    //Sets the maximum capacity of the terminal.
    volatile Integer maxCapacity = 50;
    //Sets when the ticket machine will break randomly between the lower bound and upper bound in seconds (e.g. randomly between 10000 (lower bound) and 11000 (upper bound) milliseconds from now)
    private final AtomicInteger ticketMachineBreakLowerBound = new AtomicInteger(10000);
    private final AtomicInteger ticketMachineBreakUpperBound = new AtomicInteger(11000);
    //Sets when ticket booth A will go on a break (in milliseconds) from the start of the program.
    private final AtomicInteger ticketBoothABreakStart = new AtomicInteger(2000);
    //Sets how long will ticket booth A take a break for (in milliseconds). (i.e., Break for how long?)
    private final AtomicInteger ticketBoothABreakDuration = new AtomicInteger(ThreadLocalRandom.current().nextInt(5000, 12000));
    //Sets when ticket booth B will go on a break (in milliseconds) from the start of the program.
    private final AtomicInteger ticketBoothBBreakStart = new AtomicInteger(2000);
    //Sets how long will ticket booth B take a break for (in milliseconds). (i.e, Break for how long?)
    private final AtomicInteger ticketBoothBBreakDuration = new AtomicInteger(ThreadLocalRandom.current().nextInt(5000, 12000));


    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
     * Shared Objects
     * */
    /*
     * Semaphores
     * */
    private final Semaphore breaking = new Semaphore(1);
    private final Semaphore printedFoyerFull = new Semaphore(1, true);
    /*
     * CountDownLatch
     * */
    private final CountDownLatch toiletBreaksAllowed = new CountDownLatch(2);
    /*
     * Locks
     * */
    //ReentrantLock
    //Used for the 3 TRANSFER threads, that are constantly trying to transfer the head object from Foyer to their respective queues of Ticket Machine Queue, Ticket Booth A Queue, and Ticket Booth B Queue.
    private final ReentrantLock transferLock = new ReentrantLock(true);
    //Used to block the BREAKING thread and CONSUMER thread of Ticket Machine.
    private final ReentrantLock ticketMachineLock = new ReentrantLock(true);
    //Used to block the BREAKING thread and CONSUMER thread of Ticket Booth A.
    private final ReentrantLock ticketBoothALock = new ReentrantLock(true);
    //Used to block the BREAKING thread and CONSUMER thread of Ticket Booth B.
    private final ReentrantLock ticketBoothBLock = new ReentrantLock(true);

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
     * POISON
     * */
    //POISON CUSTOMER OBJECT
    //USED FOR A GRACEFUL SHUTDOWN.
    private final Customer POISON = new Customer("POISON");
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void run() {
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        /*
         * PRODUCER THREADS
         * */
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        //PRODUCE CUSTOMERS AT EAST GATE
        Thread produceCustomerEast = new Thread(() -> {
            //Sets a busy wait loop.
            while (running.get()) {
                //Delays randomly between X to Y seconds.
                delayRandomlyBetweenXtoYSeconds(1, 4);
                //Checks to see if the simulation is still running & if the customer objects created has exceeded a 150, since only 150 customers are coming.
                if (!running.get() || customerCount.get() > 150) {
                    //Breaks the while loop if it's no longer running or customer count has exceeded 150.
                    break;
                } else {
                    //Checks to see if the terminal has reached maximum capacity by checking all the areas of the terminal (Part A: Foyer, Ticket Machine Q, Ticket Booth A Q and Ticket Booth B Q).
                    if ((foyer.size()+ticketMachineQ.size()+ticketBoothAQ.size()+ticketBoothBQ.size()) == maxCapacity) {
                        //Obtains the semaphore to see if the "terminal has reached maximum capacity" has already been printed out or not.
                        if (printedFoyerFull.availablePermits() == 0) {
                            //NOT A MISTAKE
                        } else {
                            //Tries to acquire a permit to print out "terminal has reached maximum capacity"
                            boolean printable = printedFoyerFull.tryAcquire();
                            if (!printable) {
                                //NOT A MISTAKE
                            } else {
                                //Prints out to console.
                                System.out.println(ConsoleColors.BLACK_BACKGROUND_BRIGHT + "Terminal has reached MAX Capacity of " + (foyer.size() + ticketMachineQ.size() + ticketBoothAQ.size() + ticketBoothBQ.size()) + " (Foyer Size: " + foyer.size() + " | TicketMachineQ: " + ticketMachineQ.size() + " | TicketBoothAQ: " + ticketBoothAQ.size() + " | TicketBoothBQ: " + ticketBoothBQ.size() + ")." + ConsoleColors.RESET);
                            }
                        }
                    } else {
                        //Drains the permits back to zero (This is done as there are two threads running that might constantly
                        // release permits and it can exceed the maximum instantiated amount, thus having lots of permits)
                        // We drain it to ensure any time a customer is added to the terminal, there are zero permits left.
                        printedFoyerFull.drainPermits();
                        //Then we add in one permit to ensure only one permit is always available.
                        //Adds in one permit.
                        printedFoyerFull.release(1);
                        //Creates the customer object of current count.
                        Customer c = new Customer(String.valueOf(customerCount.getAndIncrement()));
                        //Prints out to console that it is coming to the east gate.
                        System.out.println(ConsoleColors.YELLOW_BOLD_BRIGHT + c + " is coming from the east gate." + ConsoleColors.RESET);
                        try {
                            //Puts the customer object in to the foyer.
                            foyer.put(c);
                            //Checks to see if the program is still running or not after putting & if the foyer contains any POISON customer object.s
                            if (!running.get() || foyer.contains(POISON)) {
                                //Breaks the while loop if it is no longer running or foyer contains POISON.
                                break;
                            } else {
                                //Prints out to console that customer object has successfully entered the foyer.
                                System.out.println(c + " has entered foyer from east gate (Size :" + foyer.size() + ").");
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            System.out.println("PRODUCER | produceCustomerEast on " + Thread.currentThread().getName() + " has end.");
        });
        produceCustomerEast.start();
        System.out.println(ConsoleColors.WHITE_BOLD + "PRODUCER | produceCustomerEast on " + produceCustomerEast.getName() + " started." + ConsoleColors.RESET);

        //PRODUCE CUSTOMERS AT WEST GATE
        Thread produceCustomerWest = new Thread(() -> {
            //Sets a busy wait loop.
            while (running.get()) {
                //Delays randomly between X to Y seconds.
                delayRandomlyBetweenXtoYSeconds(1, 4);
                //Checks to see if the simulation is still running & if the customer objects created has exceeded a 150, since only 150 customers are coming.
                if (!running.get() || customerCount.get() > 150) {
                    //Breaks the while loop if it's no longer running or customer count has exceeded 150.
                    break;
                } else {
                    //Checks to see if the terminal has reached maximum capacity by checking all the areas of the terminal (Part A: Foyer, Ticket Machine Q, Ticket Booth A Q and Ticket Booth B Q).
                    if ((foyer.size()+ticketMachineQ.size()+ticketBoothAQ.size()+ticketBoothBQ.size())  == maxCapacity) {
                        //Obtains the semaphore to see if the "terminal has reached maximum capacity" has already been printed out or not.
                        if (printedFoyerFull.availablePermits() == 0) {
                            //NOT A MISTAKE
                        } else {
                            //Tries to acquire a permit to print out "terminal has reached maximum capacity"
                            boolean printable = printedFoyerFull.tryAcquire();
                            if (!printable) {
                                //NOT A MISTAKE
                            } else {
                                //Prints out to console.
                                System.out.println(ConsoleColors.BLACK_BACKGROUND_BRIGHT + "Terminal  has reached MAX Capacity of " + (foyer.size() + ticketMachineQ.size() + ticketBoothAQ.size() + ticketBoothBQ.size()) + " (Foyer Size: " + foyer.size() + " | TicketMachineQ: " + ticketMachineQ.size() + " | TicketBoothAQ: " + ticketBoothAQ.size() + " | TicketBoothBQ: " + ticketBoothBQ.size() + ")." + ConsoleColors.RESET);
                            }
                        }
                    } else {
                        //Drains the permits back to zero (This is done as there are two threads running that might constantly
                        // release permits and it can exceed the maximum instantiated amount, thus having lots of permits)
                        // We drain it to ensure any time a customer is added to the terminal, there are zero permits left.
                        printedFoyerFull.drainPermits();
                        //Then we add in one permit to ensure only one permit is always available.
                        //Adds in one permit.
                        printedFoyerFull.release(1);
                        //Creates the customer object of current count.
                        Customer c = new Customer(String.valueOf(customerCount.getAndIncrement()));
                        //Prints out to console that it is coming to the west gate.
                        System.out.println(ConsoleColors.YELLOW_BOLD + c + " is coming from the west gate." + ConsoleColors.RESET);
                        try {
                            //Puts the customer object into the foyer.
                            foyer.put(c);
                            //Checks to see if the program is still running or not after putting & if the foyer contains any POISON customer object.s
                            if (!running.get() || foyer.contains(POISON)) {
                                //Breaks the while loop if it is no longer running or foyer contains POISON.
                                break;
                            } else {
                                //Prints out to console that customer object has successfully entered the foyer.
                                System.out.println(c + " has entered foyer from west gate (Size :" + foyer.size() + ").");
                            }
                        } catch (InterruptedException e) {
                            //Catches if the thread is interrupted while trying to use the .put() method.
                            e.printStackTrace();
                        }
                    }
                }
            }
            //Prints out that this thread has ended since it has successfully run it's run method to the end.
            System.out.println("PRODUCER | produceCustomerWest on " + Thread.currentThread().getName() + " has end.");
        });
        produceCustomerWest.start();
        //Prints out that this thread is started.
        System.out.println(ConsoleColors.WHITE_BOLD + "PRODUCER | produceCustomerWest on " + produceCustomerWest.getName() + " started." + ConsoleColors.RESET);


        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        /*
         * TRANSFER THREADS
         * */
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        //TRANSFER TO TICKET MACHINE QUEUE.
        Thread transferToTicketMachineQ = new Thread(() -> {
            //Sets a busy wait loop.
            while (running.get()) {
                //If the ticket machine's queue is closed, this happens when the ticket machine breaks down.
                if (!ticketMachineQOpen.get()) {
                    //Checks to see if the ticket machine's queue is empty or not.
                    if (ticketMachineQ.size() == 0) {
                        //NOT A MISTAKE
                    } else {
                        //If the ticket machine's queue is not empty.
                        //This thread will take the customer objects from the queue and transfer it to other queues.
                        try {
                            //Polls the customer object from the ticket machine's queue.
                            Customer c = ticketMachineQ.poll(1, TimeUnit.SECONDS);
                            //If it is a POISON object, it will break this entire while loop.
                            if (c == POISON) {
                                break;
                            } else {
                                //If it is null.. does nothing.
                                if (c == null) {
                                    //NOT A MISTAKE
                                } else {
                                    //Otherwise, by random, this thread will put the customer object to either Ticket Booth A's queue or Ticket Booth B's Queue.
                                    if (ThreadLocalRandom.current().nextBoolean()) {
                                        //Puts customer object to Ticket Booth A's queue.
                                        ticketBoothAQ.put(c);
                                        //Prints out to console that the customer has successfully been put to another queue.
                                        System.out.println("Since Ticket Machine broke... " + c + " goes from Ticket Machine Queue ➔ Ticket Booth A Queue");
                                    } else {
                                        //Puts customer object to Ticket Booth B's queue.
                                        ticketBoothBQ.put(c);
                                        //Prints out to console that the customer has successfully been put to another queue.
                                        System.out.println("Since Ticket Machine broke... " + c + " goes from Ticket Machine Queue ➔ Ticket Booth B Queue");
                                    }
                                }
                            }
                        } catch (InterruptedException e) {
                            //Catches if the thread is interrupted while trying to use the .put() method.
                            e.printStackTrace();
                        }
                    }
                } else {
                    //Checks to see if the ticket machine's queue is full or not.
                    if (ticketMachineQ.remainingCapacity() == 0) {
                        //If it is full... does nothing, restarts the loop.
                        //NOT A MISTAKE
                    } else {
                        //If there is space in the Ticket Machine's queue.
                        //It will obtain the ReentrantLock for transferring.
                        //This is done to ensure that there is no Thread Interference when transferring the customer object from the foyer to the Ticket Machine's queue.
                        transferLock.lock();
                        try {
                            //Retrieves but does NOT remove the customer object from the head of foyer's ArrayBlockingQueue.
                            Customer c = foyer.peek();
                            //If it is a POISON object, it will break this entire while loop.
                            if (c == POISON) {
                                break;
                            } else {
                                //If it is null.... does nothing.. restarts the loop operation.
                                if (c == null) {
                                    //Does nothing...
                                    //NOT A MISTAKE
                                } else {
                                    //Tries to offer the customer object to the Ticket Machine's queue.
                                    boolean b = ticketMachineQ.offer(c);
                                    //If it is unsuccessful.... b= false.
                                    if (!b) {
                                        //Prints out to console stating that this customer object is staying at the foyer as the Ticket Machine's queue is full.
                                        System.out.println(ConsoleColors.PURPLE + "︾ " + c + " staying at Foyer, unable to queue at Ticket Machine's queue as it is full" + ConsoleColors.RESET);
                                    } else {
                                        //Removes the customer object from the foyer as it successfully moved to the Ticket Machine's queue.
                                        foyer.remove(c);
                                        //Prints out to console stating that this customer has successfully moved from the foyer to the Ticket Machine's queue.
                                        System.out.println(ConsoleColors.PURPLE + "From Foyer, ↕ " + c + " queueing at Ticket Machine (Size :" + ticketMachineQ.size() + ")." + ConsoleColors.RESET);
                                    }
                                }
                            }
                        } finally {
                            //Finally releases the ReentrantLock.
                            transferLock.unlock();
                        }
                    }
                }
            }
            //Prints out that this thread has ended since it has successfully run it's run method to the end.
            System.out.println("TRANSFER | Foyer to Ticket Machine on " + Thread.currentThread().getName() + " has end.");
        });
        transferToTicketMachineQ.start();
        //Prints out that this thread is started.
        System.out.println(ConsoleColors.WHITE_BOLD + "TRANSFER | Foyer to Ticket Machine Queue on " + transferToTicketMachineQ.getName() + " started." + ConsoleColors.RESET);

        //TRANSFER TO TICKET BOOTH A QUEUE.
        Thread transferToTicketBoothAQ = new Thread(() -> {
            //Sets a busy wait loop.
            while (running.get()) {
                //If the ticket booth A's queue is not open.... this happens when Ticket Booth A has gone for a toilet break.
                if (!ticketBoothAQOpen.get()) {
                    //NOT A MISTAKE
                } else {
                    //If the Ticket Booth A's queue is full...
                    if (ticketBoothAQ.remainingCapacity() == 0) {
                        //If it is full... does nothing, restarts the loop.
                        //NOT A MISTAKE
                    } else {
                        //If there is space in the Ticket Booth A's queue.
                        //It will obtain the ReentrantLock for transferring.
                        //This is done to ensure that there is no Thread Interference when transferring the customer object from the foyer to the Ticket Booth A's queue.
                        transferLock.lock();
                        try {
                            //Retrieves but does NOT remove the customer object from the head of foyer's ArrayBlockingQueue.
                            Customer c = foyer.peek();
                            //If it is a POISON object, it will break this entire while loop.
                            if (c == POISON) {
                                break;
                            } else {
                                //If it is null.... does nothing.. restarts the loop operation.
                                if (c == null) {
                                    //Does nothing....
                                    //NOT A MISTAKE
                                } else {
                                    //Tries to offer the customer object to the Ticket Booth A's queue.
                                    boolean b = ticketBoothAQ.offer(c);
                                    //If it is unsuccessful.... b = false.
                                    if (!b) {
                                        //Prints out to console stating that this customer object is staying at the foyer as the Ticket Booth A's queue is full.
                                        System.out.println(ConsoleColors.PURPLE + "︾ " + c + " staying at Foyer, unable to queue at Ticket Booth A's queue as it is full" + ConsoleColors.RESET);
                                    } else {
                                        //Removes the customer object from the foyer as it successfully moved to the Ticket Booth A's queue.
                                        foyer.remove(c);
                                        //Prints out to console stating that this customer has successfully moved from the foyer to the Ticket Booth A's queue.
                                        System.out.println(ConsoleColors.PURPLE + "From Foyer, ↕ " + c + " queueing at Ticket Booth A (Size :" + ticketBoothAQ.size() + ")." + ConsoleColors.RESET);
                                    }
                                }
                            }
                        } finally {
                            //Finally releases the ReentrantLock.
                            transferLock.unlock();
                        }
                    }
                }
            }
            //Prints out that this thread has ended since it has successfully run it's run method to the end.
            System.out.println("TRANSFER | Foyer to Ticket Booth A on " + Thread.currentThread().getName() + " has end.");
        });
        transferToTicketBoothAQ.start();
        //Prints out that this thread is started.
        System.out.println(ConsoleColors.WHITE_BOLD + "TRANSFER | Foyer to Ticket Booth A on " + transferToTicketBoothAQ.getName() + " started." + ConsoleColors.RESET);

        //TRANSFER TO TICKET BOOTH B QUEUE.
        Thread transferToTicketBoothBQ = new Thread(() -> {
            //Sets a busy wait loop.
            while (running.get()) {
                //If the ticket booth B's queue is not open.... this happens when Ticket Booth B has gone for a toilet break.
                if (!ticketBoothBQOpen.get()) {
                    //NOT A MISTAKE
                } else {
                    //If the Ticket Booth A's queue is full...
                    if (ticketBoothBQ.remainingCapacity() == 0) {
                        //If it is full... does nothing, restarts the loop.
                        //NOT A MISTAKE
                    } else {
                        //If there is space in the Ticket Booth B's queue.
                        //It will obtain the ReentrantLock for transferring.
                        //This is done to ensure that there is no Thread Interference when transferring the customer object from the foyer to the Ticket Booth B's queue.
                        transferLock.lock();
                        try {
                            //Retrieves but does NOT remove the customer object from the head of foyer's ArrayBlockingQueue.
                            Customer c = foyer.peek();
                            //If it is a POISON object, it will break this entire while loop.
                            if (c == POISON) {
                                break;
                            } else {
                                //If it is null.... does nothing.. restarts the loop operation.
                                if (c == null) {
                                    //Does nothing....
                                    //NOT A MISTAKE
                                } else {
                                    //Tries to offer the customer object to the Ticket Booth B's queue.
                                    boolean b = ticketBoothBQ.offer(c);
                                    //If it is unsuccessful.... b = false.
                                    if (!b) {
                                        //Prints out to console stating that this customer object is staying at the foyer as the Ticket Booth A's queue is full.
                                        System.out.println(ConsoleColors.PURPLE + "︾ " + c + " staying at Foyer, unable to queue at Ticket Booth B's queue as it is full" + ConsoleColors.RESET);
                                    } else {
                                        //Removes the customer object from the foyer as it successfully moved to the Ticket Booth B's queue.
                                        foyer.remove(c);
                                        //Prints out to console stating that this customer has successfully moved from the foyer to the Ticket Booth B's queue.
                                        System.out.println(ConsoleColors.PURPLE + "From Foyer, ↕ " + c + " queueing at Ticket Booth B (Size :" + ticketBoothBQ.size() + ")." + ConsoleColors.RESET);
                                    }
                                }
                            }
                        } finally {
                            //Finally releases the ReentrantLock.
                            transferLock.unlock();
                        }
                    }
                }
            }
            //Prints out that this thread has ended since it has successfully run it's run method to the end.
            System.out.println("TRANSFER | Foyer to Ticket Booth B on " + Thread.currentThread().getName() + " has end.");
        });
        transferToTicketBoothBQ.start();
        //Prints out that this thread is started.
        System.out.println(ConsoleColors.WHITE_BOLD + "TRANSFER | Foyer to Ticket Booth B on " + transferToTicketBoothBQ.getName() + " started." + ConsoleColors.RESET);


        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        /*
         * BREAKING THREADS
         * */
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //TICKET MACHINE BREAK
        Thread ticketMachineBreak = new Thread(() -> {
            //Sets a busy wait loop.
            while (running.get()) {
                // Checks to see if there are no more permits, if there are no more permits, breaks the entire while loop. (i.e., this happens when the Ticket Machine has no more allowed chances to break in this simulation, thus if it sees breaking.availablePermits() is 0, then that-
                // - means that this Ticket Machine is not allowed to break anymore. This also acts as an alternative implementation, as we can alter the code slightly to set a delay to re-enable the ticket machine instead of breaking it once and for all,
                //  but this is not required in the assignment. Therefore, it is only used as a SAFEGUARD to end this thread when it checks to see that the ticket machine has already broke.)
                if (breaking.availablePermits() == 0) {
                    break;
                } else {
                    //Using a boolean randomizer, if it is true, then...
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        //Prints out to console stating that the ticket machine will break in this run of the simulation, between x and y seconds from the start.
                        System.out.println(ConsoleColors.RED + "BREAKER | Will ticket machine break on this run? YES!" + " approximately between " + ticketMachineBreakLowerBound.get() / 1000 + " and " + ticketMachineBreakUpperBound.get() / 1000 + " seconds from now." + ConsoleColors.RESET);
                        //Sleeps this thread for x to y seconds from the start.
                        delayRandomlyBetweenXToYMilliseconds(ticketMachineBreakLowerBound.get(), ticketMachineBreakUpperBound.get());
                        //Once it stops sleeping... it will obtain the ticketMachineLock ReentrantLock.
                        ticketMachineLock.lock();
                        try {
                            //Sets the running and queue open flags to false.
                            ticketMachineRunning.set(false);
                            ticketMachineQOpen.set(false);
                            //Prints out to console stating that the ticket machine has broke.
                            System.out.println(ConsoleColors.RED_BOLD + "BREAKER | Ticket Machine has BROKE!" + ConsoleColors.RESET);
                            //Acquires (decrement) the permits of the "breaking" semaphore. Meaning now we know that it has broke once.
                            breaking.acquire();
                        } catch (InterruptedException e) {
                            //Catches interruption to this thread if it was blocked trying to acquire the breaking semaphore. Doesn't happen in any case.
                            e.printStackTrace();
                        } finally {
                            //Finally relinquishes the ticketMachineLock Reentrant Lock.
                            ticketMachineLock.unlock();
                        }
                    } else {
                        //If the boolean randomizer = false, then the ticket machine will not break in this run of the simulation.
                        System.out.println(ConsoleColors.RED + "BREAKER | Will ticket machine break on this run? NO" + "" + ConsoleColors.RESET);
                        //Breaks the entire while loop.
                        break;
                    }
                }
            }
            //Prints out that this thread has ended since it has successfully run it's run method to the end.
            System.out.println("BREAKER | Ticket Machine Breaker on " + Thread.currentThread().getName() + " has ended.");
        });
        ticketMachineBreak.start();
        //Prints out that this thread is started.
        System.out.println(ConsoleColors.WHITE_BOLD + "BREAKER | Ticket Machine Breaker on " + ticketMachineBreak.getName() + " started." + ConsoleColors.RESET);

        //TICKET BOOTH A BREAK
        Thread ticketBoothABreak = new Thread(() -> {
            //Sets a busy wait loop.
            while (running.get()) {
                //Checks to see if there is no more counts in the countdown latch or not.(i.e., this happens when the Ticket Booth A has no more allowed chances to break in this simulation, thus if it sees toiletBreaksAllowed.getCount() is 0, then that-
                // -means that this Ticket Booth A is not allowed to break anymore. Therefore, it acts as a guard to set how many times this thread is allowed to run to make Ticket Booth A go on a toilet break.)
                //- If there are no more counts in the CountDownLatch, it will break the entire loop.
                if (toiletBreaksAllowed.getCount() == 0) {
                    //Prints out to console stating that no more toilet booths are allowed to go on a break anymore as the count is now == 0.
                    //System.out.println(ConsoleColors.CYAN + "No toilet booths are allowed to take a break anymore!.");
                    //Breaks the entire while loop.
                    break;
                } else {
                    //If there are available counts in the CountDownLatch, then we will count it down, indicating that Ticket Booth A will indeed go on a break at some point.
                    toiletBreaksAllowed.countDown();
                    //Prints out to console stating that Ticket Booth A will gon a break X seconds from now for Y seconds.
                    System.out.println(ConsoleColors.RED + "BREAKER | Ticket Booth A will go on a toilet \uD83D\uDEBD break at " + ticketBoothABreakStart.get() / 1000 + " seconds from now for " + ticketBoothABreakDuration.get() / 1000 + " seconds." + ConsoleColors.RESET);
                    //Sleeps this thread of X seconds from the start before beginning the toilet break.
                    delay(ticketBoothABreakStart.get());
                    //Checks to see if the program is still running, by checking the running flag.
                    if (running.get()) {
                        //If it is running...
                        //It will obtain the ticketBoothALock ReentrantLock. This lock is used by the corresponding CONSUMER | Ticket Booth A thread.. which will try to obtain this lock to run it's operation... however
                        // -since the lock is obtained when Ticket Booth A is going on a toilet break, the corresponding CONSUMER | Ticket Booth A thread will be blocked from running... thus truly stopping the CONSUMER | Ticket Booth A
                        // -thread to stop working as it is now on a toilet break! Prevents a slipped condition here!
                        //Obtains the ticketBoothALock
                        ticketBoothALock.lock();
                        try {
                            //Sets the running flag to false.
                            ticketBoothARunning.set(false);
                            //Prints out to console stating that Ticket Booth A has gone for a toilet break for Y seconds.
                            System.out.println(ConsoleColors.RED_BOLD + "BREAKER | Ticket Booth A has gone on toilet break for " + ticketBoothABreakDuration.get() / 1000 + " seconds! \uD83D\uDEBD" + ConsoleColors.RESET);
                            //Sleeps this thread for Y seconds.
                            delay(ticketBoothABreakDuration.get());
                            //Checks again to see if this program is still running...
                            //If it is NOT running...
                            if (!running.get()) {
                                //Breaks the entire while loop.
                                break;
                            } else {
                                //Sets the running flag back to false.
                                ticketBoothARunning.set(true);
                                //Prints out to console stating that Ticket Booth A has come back from the toilet break after Y seconds!.
                                System.out.println(ConsoleColors.RED_BOLD + "BREAKER | Ticket Booth A is back after a " + ticketBoothABreakDuration.get() / 1000 + " second break! \uD83D\uDEBD" + ConsoleColors.RESET);
                            }
                        } finally {
                            //Finally releases the ticketBoothALock ReentrantLock.
                            ticketBoothALock.unlock();
                        }
                    }
                }
            }
            //Prints out that this thread has ended since it has successfully run it's run method to the end.
            System.out.println("BREAKER | Ticket Booth A on " + Thread.currentThread().getName() + " ended.");
        });
        ticketBoothABreak.start();
        //Prints out that this thread is started.
        System.out.println(ConsoleColors.WHITE_BOLD + "BREAKER | Ticket Booth A on " + ticketBoothABreak.getName() + " started." + ConsoleColors.RESET);

        //TICKET BOOTH B BREAK
        Thread ticketBoothBBreak = new Thread(() -> {
            //Sets a busy wait loop.
            while (running.get()) {
                //Checks to see if there is no more counts in the countdown latch or not.(i.e., this happens when the Ticket Booth A has no more allowed chances to break in this simulation, thus if it sees toiletBreaksAllowed.getCount() is 0, then that-
                // -means that this Ticket Booth A is not allowed to break anymore. Therefore, it acts as a guard to set how many times this thread is allowed to run to make Ticket Booth A go on a toilet break.)
                //- If there are no more counts in the CountDownLatch, it will break the entire loop.
                if (toiletBreaksAllowed.getCount() == 0) {
                    //Prints out to console stating that no more toilet booths are allowed to go on a break anymore as the count is now == 0.
                    //System.out.println(ConsoleColors.CYAN + "No toilet booths are allowed to take a break anymore!.");
                    //Breaks the entire while loop.
                    break;
                } else {
                    //If there are available counts in the CountDownLatch, then we will count it down, indicating that Ticket Booth B will indeed go on a break at some point.
                    toiletBreaksAllowed.countDown();
                    //Prints out to console stating that Ticket Booth B will gon a break X seconds from now for Y seconds.
                    System.out.println(ConsoleColors.RED + "BREAKER | Ticket Booth B will go on a toilet \uD83D\uDEBD break at " + ticketBoothABreakStart.get() / 1000 + " seconds from now for " + ticketBoothBBreakDuration.get() / 1000 + " seconds." + ConsoleColors.RESET);
                    //Sleeps this thread of X seconds from the start before beginning the toilet break.
                    delay(ticketBoothBBreakStart.get());
                    //Checks to see if the program is still running, by checking the running flag.
                    if (running.get()) {
                        //If it is running...
                        //It will obtain the ticketBoothBLock ReentrantLock. This lock is used by the corresponding CONSUMER | Ticket Booth B thread.. which will try to obtain this lock to run it's operation... however
                        // -since the lock is obtained when Ticket Booth B is going on a toilet break, the corresponding CONSUMER | Ticket Booth B thread will be blocked from running... thus truly stopping the CONSUMER | Ticket Booth B
                        // -thread to stop working as it is now on a toilet break! Prevents a slipped condition here!
                        //Obtains the ticketBoothBLock
                        ticketBoothBLock.lock();
                        try {
                            //Sets the running flag to false.
                            ticketBoothBRunning.set(false);
                            //Prints out to console stating that Ticket Booth B has gone for a toilet break for Y seconds.
                            System.out.println(ConsoleColors.RED_BOLD + "BREAKER | Ticket Booth B has gone on toilet break for " + ticketBoothBBreakDuration.get() / 1000 + " seconds! \uD83D\uDEBD" + ConsoleColors.RESET);
                            //Sleeps this thread for Y seconds.
                            delay(ticketBoothBBreakDuration.get());
                            //Checks again to see if this program is still running...
                            //If it is NOT running...
                            if (!running.get()) {
                                //Breaks the entire while loop.
                                break;
                            } else {
                                //Sets the running flag back to false.
                                ticketBoothBRunning.set(true);
                                //Prints out to console stating that Ticket Booth A has come back from the toilet break after Y seconds!.
                                System.out.println(ConsoleColors.RED_BOLD + "BREAKER | Ticket Booth B is back after a " + ticketBoothBBreakDuration.get() / 1000 + " second break! \uD83D\uDEBD" + ConsoleColors.RESET);
                            }
                        } finally {
                            //Finally releases the ticketBoothALock ReentrantLock.
                            ticketBoothBLock.unlock();
                        }
                    }
                }
            }
            //Prints out that this thread has ended since it has successfully run it's run method to the end.
            System.out.println("BREAKER | Ticket Booth B on " + Thread.currentThread().getName() + " ended.");
        });
        ticketBoothBBreak.start();
        //Prints out that this thread is started.
        System.out.println(ConsoleColors.WHITE_BOLD + "BREAKER | Ticket Booth B on " + ticketBoothBBreak.getName() + " started." + ConsoleColors.RESET);


        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        /*
         * CONSUMER THREADS
         * */
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //TICKET MACHINE
        Thread ticketMachine = new Thread(() -> {
            //Sets a busy wait loop.
            while (running.get()) {
                //Checks to see if the Ticket Machine is set running or not.
                //If it is NOT running...
                if (!ticketMachineRunning.get()) {
                    //Does nothing... repeats the loop.
                    //NOT A MISTAKE
                } else {
                    //If it is running it...
                    //It will try to obtain the ticketMachineLock ReentrantLock.
                    //This thread shares this lock with the corresponding BREAKING | Ticket Machine thread. If the BREAKING | Ticket Machine thread is indeed trying to break the operation of this CONSUMER | Ticket Machine,
                    // then it will obtain this lock and block out this thread from trying to issue a ticket.
                    ticketMachineLock.lock();
                    //Checks to see after it has obtained the lock to see if this Ticket Machine is still running or not.
                    //If it is set to not run..
                    if(!ticketMachineRunning.get()){
                        //Break the entire while loop.
                        break;
                    } else {
                        try {
                            //Retrieves but does NOT remove the head of the Ticket Machine queue's customer object.
                            Customer c = ticketMachineQ.peek();
                            //If it is a POISON object..
                            if (c == POISON) {
                                //Breaks the entire while loop.
                                break;
                            } else {
                                //If it is NOT POISON..
                                //If it is null...
                                if (c == null) {
                                    //Does nothing... repeats the entire while loop.
                                    //NOT A MISTAKE
                                } else {
                                    //If it is NOT null..
                                    //Sleeps this thread for 4 seconds (as stated in the assignment question). Takes 4 seconds to issue a ticket.
                                    delay(ticketMachineIssueDuration);
                                    //After sleeping, checks again to see if the Ticket Machine is running or not & checks to see if the Ticket Machine's queue is closed or not. The second check is a little bit pointless but it is there either way as a failsafe just in case.
                                    if (!ticketMachineRunning.get() || !ticketMachineQOpen.get()) {
                                        //Does nothing...
                                        // NOT A MISTAKE
                                    } else {
                                        //Using a randomizer, issues a ticket either to location 1, 2 or 3.
                                        int location = ThreadLocalRandom.current().nextInt(1, 3);
                                        //Prints out to console stating that the customer object has finally bought a ticket to location 1, 2 or 3.
                                        System.out.println(ConsoleColors.BLUE_BOLD_BRIGHT + c + " has bought a ticket to location " + location + " from the Ticket Machine \uD83C\uDF9F\uD83C\uDF9F\uD83C\uDF9F" + ConsoleColors.RESET);
                                        //Then, using a switch, the customer object is placed into the respective Waiting Area(s) based on the location that they are going.
                                        switch (location) {
                                            case 1:
                                                //If location = 1, the customer object will be put at the head of Waiting Area 1.
                                                waitingArea1.putFirst(c);
                                                //The customer object is then removed from the Ticket Machine's queue.
                                                ticketMachineQ.remove(c);
                                                //Prints out to console stating that the customer object is now waiting at Waiting Area 1.
                                                System.out.println(ConsoleColors.BLUE_BOLD + c + " is now waiting at Waiting Area 1." + ConsoleColors.RESET);
                                                break;
                                            case 2:
                                                //If location = 1, the customer object will be put at the head of Waiting Area 1.
                                                waitingArea2.putFirst(c);
                                                //The customer object is then removed from the Ticket Machine's queue.
                                                ticketMachineQ.remove(c);
                                                //Prints out to console stating that the customer object is now waiting at Waiting Area 2.
                                                System.out.println(ConsoleColors.BLUE_BOLD + c + " is now waiting at Waiting Area 2." + ConsoleColors.RESET);
                                                break;
                                            case 3:
                                                //If location = 2, the customer object will be put at the head of Waiting Area 3.
                                                waitingArea3.putFirst(c);
                                                //The customer object is then removed from the Ticket Machine's queue.
                                                ticketMachineQ.remove(c);
                                                //Prints out to console stating that the customer object is now waiting at Waiting Area 3.
                                                System.out.println(ConsoleColors.BLUE_BOLD + c + " is now waiting at Waiting Area 3." + ConsoleColors.RESET);
                                                break;
                                        }
                                    }
                                }
                            }
                        } catch (InterruptedException e) {
                            //Catches interruption if this thread was interrupted when it was sleeping (issuing a ticket) or when it was trying to put the customer object into their respective Waiting Area(s).
                            e.printStackTrace();
                        } finally {
                            //Finally relinquishes the ticketMachineLock ReentrantLock
                            ticketMachineLock.unlock();
                        }
                    }
                }
            }
            //Prints out that this thread has ended since it has successfully run it's run method to the end.
            System.out.println("CONSUMER | Ticket Machine on " + Thread.currentThread().getName() + " has end.");
        });
        ticketMachine.start();
        //Prints out that this thread is started.
        System.out.println(ConsoleColors.WHITE_BOLD + "CONSUMER | Ticket Machine on " + ticketMachine.getName() + " started." + ConsoleColors.RESET);

        //TICKET BOOTH A
        Thread ticketBoothA = new Thread(() -> {
            //Sets a busy wait loop.
            while (running.get()) {
                //Checks to see if Ticket Booth A is running or not.
                //If it is NOT running...
                if (!ticketBoothARunning.get()) {
                    //Does nothing.. repeats the loop.
                    //NOT A MISTAKE
                } else {
                    //If it is running it...
                    //It will try to obtain the ticketBoothALock ReentrantLock.
                    //This thread shares this lock with the corresponding BREAKING | Ticket Booth A thread. If BREAKING | Ticket Booth A thread is indeed trying to break the operation of this CONSUMER | Ticket Booth A
                    // then it will try to obtain this lock and block out this thread from trying to issue a ticket.
                    ticketBoothALock.lock();
                    //Checks to see after it has obtained the lock to see if this Ticket Booth A is still running or not.
                    //IF it is set to not run...
                    if(!ticketBoothARunning.get()){
                        //Break the entire while loop.
                        break;
                    } else {
                        try {
                            //Retrieves but does NOT remove the head of the Ticket Booth A queue's customer object.
                            Customer c = ticketBoothAQ.peek();
                            //If it is a POISON object..
                            if (c == POISON) {
                                //Breaks the entire while loop.
                                break;
                            } else {
                                //If it is NOT POISON..
                                //If it is null...
                                if (c == null) {
                                    //Does nothing... repeats the entire while loop.
                                    //NOT A MISTAKE;
                                } else {
                                    //If it is NOT null...
                                    //Sleeps this thread for 8 seconds (as stated in the assignment question). Takes 8 seconds to issue a ticket.
                                    delay(ticketBoothIssueDuration);
                                    //After sleeping, checks again to see if the Ticket Booth A is running or not.
                                    if (!ticketBoothARunning.get()) {
                                        //Does nothing... repeats the entire while loop.
                                        //NOT A MISTAKE
                                    } else {
                                        //Using a randomizer, issues a ticket either to location 1, 2 or 3.
                                        int location = ThreadLocalRandom.current().nextInt(1, 3);
                                        //Prints out to console stating that the customer object has finally bought a ticket to location 1, 2 or 3.
                                        System.out.println(ConsoleColors.BLUE_BOLD_BRIGHT + c + " has bought a ticket to location " + location + " from Ticket Booth A  \uD83C\uDF9F\uD83C\uDF9F\uD83C\uDF9F" + ConsoleColors.RESET);
                                        //Then, using a switch, the customer object is placed into the respective Waiting Area(s) based on the location that they are going.
                                        switch (location) {
                                            case 1:
                                                //If location = 1, the customer object will be put at the head of Waiting Area 1.
                                                waitingArea1.putFirst(c);
                                                //The customer object is then removed from the Ticket Machine's queue.
                                                ticketBoothAQ.remove(c);
                                                //Prints out to console stating that the customer object is now waiting at Waiting Area 1.
                                                System.out.println(ConsoleColors.BLUE_BOLD + c + " is now waiting at Waiting Area 1." + ConsoleColors.RESET);
                                                break;
                                            case 2:
                                                //If location = 1, the customer object will be put at the head of Waiting Area 1.
                                                waitingArea2.putFirst(c);
                                                //The customer object is then removed from the Ticket Machine's queue.
                                                ticketBoothAQ.remove(c);
                                                //Prints out to console stating that the customer object is now waiting at Waiting Area 2.
                                                System.out.println(ConsoleColors.BLUE_BOLD + c + " is now waiting at Waiting Area 2." + ConsoleColors.RESET);
                                                break;
                                            case 3:
                                                //If location = 2, the customer object will be put at the head of Waiting Area 3.
                                                waitingArea3.putFirst(c);
                                                //The customer object is then removed from the Ticket Machine's queue.
                                                ticketBoothAQ.remove(c);
                                                //Prints out to console stating that the customer object is now waiting at Waiting Area 3.
                                                System.out.println(ConsoleColors.BLUE_BOLD + c + " is now waiting at Waiting Area 3." + ConsoleColors.RESET);
                                                break;
                                        }
                                    }
                                }
                            }
                        } catch (InterruptedException e) {
                            //Catches interruption if this thread was interrupted when it was sleeping (issuing a ticket) or when it was trying to put the customer object into their respective Waiting Area(s).
                            e.printStackTrace();
                        } finally {
                            //Finally relinquishes the ticketBoothALock ReentrantLock
                            ticketBoothALock.unlock();
                        }
                    }
                }
            }
            //Prints out that this thread has ended since it has successfully run it's run method to the end.
            System.out.println("CONSUMER | Ticket Booth A on " + Thread.currentThread().getName() + " has end.");
        });
        ticketBoothA.start();
        //Prints out that this thread is started.
        System.out.println(ConsoleColors.WHITE_BOLD + "CONSUMER | Ticket Booth A on " + ticketBoothA.getName() + " started." + ConsoleColors.RESET);

        //TICKET BOOTH B
        Thread ticketBoothB = new Thread(() -> {
            //Sets a busy wait loop.
            while (running.get()) {
                //Checks to see if Ticket Booth B is running or not.
                //If it is NOT running...
                if (!ticketBoothBRunning.get()) {
                    //Does nothing.. repeats the loop.
                    //NOT A MISTAKE
                } else {
                    //If it is running it...
                    //It will try to obtain the ticketBoothBLock ReentrantLock.
                    //This thread shares this lock with the corresponding BREAKING | Ticket Booth B thread. If BREAKING | Ticket Booth B thread is indeed trying to break the operation of this CONSUMER | Ticket Booth B
                    // then it will try to obtain this lock and block out this thread from trying to issue a ticket.
                    ticketBoothBLock.lock();
                    //Checks to see after it has obtained the lock to see if this Ticket Booth B is still running or not.
                    //IF it is set to not run...
                    if(!ticketBoothBRunning.get()){
                        //Break the entire while loop.
                        break;
                    } else {
                        try {
                            //Retrieves but does NOT remove the head of the Ticket Booth A queue's customer object.
                            Customer c = ticketBoothBQ.peek();
                            //If it is a POISON object..
                            if (c == POISON) {
                                //Breaks the entire while loop.
                                break;
                            } else {
                                //If it is NOT POISON..
                                //If it is null...
                                if (c == null) {
                                    //Does nothing... repeats the entire while loop.
                                    //NOT A MISTAKE
                                } else {
                                    //If it is NOT null...
                                    //Sleeps this thread for 8 seconds (as stated in the assignment question). Takes 8 seconds to issue a ticket.
                                    delay(ticketBoothIssueDuration);
                                    //After sleeping, checks again to see if the Ticket Booth B is running or not.
                                    if (!ticketBoothBRunning.get()) {
                                        //Does nothing... repeats the entire while loop.
                                        //NOT A MISTAKE
                                    } else {
                                        //Using a randomizer, issues a ticket either to location 1, 2 or 3.
                                        int location = ThreadLocalRandom.current().nextInt(1, 3);
                                        //Prints out to console stating that the customer object has finally bought a ticket to location 1, 2 or 3.
                                        System.out.println(ConsoleColors.BLUE_BOLD_BRIGHT + c + " has bought a ticket to location " + location + " from Ticket Booth B \uD83C\uDF9F\uD83C\uDF9F\uD83C\uDF9F" + ConsoleColors.RESET);
                                        //Then, using a switch, the customer object is placed into the respective Waiting Area(s) based on the location that they are going.
                                        switch (location) {
                                            case 1:
                                                //If location = 1, the customer object will be put at the head of Waiting Area 1.
                                                waitingArea1.putFirst(c);
                                                //The customer object is then removed from the Ticket Machine's queue.
                                                ticketBoothBQ.remove(c);
                                                //Prints out to console stating that the customer object is now waiting at Waiting Area 1.
                                                System.out.println(ConsoleColors.BLUE_BOLD + c + " is now waiting at Waiting Area 1." + ConsoleColors.RESET);
                                                break;
                                            case 2:
                                                //If location = 1, the customer object will be put at the head of Waiting Area 1.
                                                waitingArea2.putFirst(c);
                                                //The customer object is then removed from the Ticket Machine's queue.
                                                ticketBoothBQ.remove(c);
                                                //Prints out to console stating that the customer object is now waiting at Waiting Area 2.
                                                System.out.println(ConsoleColors.BLUE_BOLD + c + " is now waiting at Waiting Area 2." + ConsoleColors.RESET);
                                                break;
                                            case 3:
                                                //If location = 2, the customer object will be put at the head of Waiting Area 3.
                                                waitingArea3.putFirst(c);
                                                //The customer object is then removed from the Ticket Machine's queue.
                                                ticketBoothBQ.remove(c);
                                                //Prints out to console stating that the customer object is now waiting at Waiting Area 3.
                                                System.out.println(ConsoleColors.BLUE_BOLD + c + " is now waiting at Waiting Area 3." + ConsoleColors.RESET);
                                                break;
                                        }
                                    }
                                }
                            }
                        } catch (InterruptedException e) {
                            //Catches interruption if this thread was interrupted when it was sleeping (issuing a ticket) or when it was trying to put the customer object into their respective Waiting Area(s).
                            e.printStackTrace();
                        } finally {
                            //Finally relinquishes the ticketBoothALock ReentrantLock
                            ticketBoothBLock.unlock();
                        }
                    }
                }
            }
            //Prints out that this thread has ended since it has successfully run it's run method to the end.
            System.out.println("CONSUMER | Ticket Booth B on " + Thread.currentThread().getName() + " has end.");
        });
        ticketBoothB.start();
        //Prints out that this thread is started.
        System.out.println(ConsoleColors.WHITE_BOLD + "CONSUMER | Ticket Booth B on " + ticketBoothB.getName() + " started." + ConsoleColors.RESET);


        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        /*
         * POISON THREAD (Kills all threads by inserting a poison object)
         * */
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        Thread poisonThread = new Thread(() -> {
            //Sleeps for 30 seconds (as per requested in the assignment).
            delay(30000);
            //Sets running flag to false.
            running.set(false);
            //Prints out to console.
            System.out.println(ConsoleColors.WHITE + "POISON | Graceful ending initiated! running.set(false) is set!" + ConsoleColors.RESET);
            /*
             * Clears all the ArrayBlockingQueue(s) & Adds POISON object.
             */
            foyer.clear();
            foyer.add(POISON);
            ticketMachineQ.clear();
            ticketMachineQ.add(POISON);
            ticketBoothAQ.clear();
            ticketBoothAQ.add(POISON);
            ticketBoothBQ.clear();
            ticketBoothBQ.add(POISON);

            //Prints out to console the statuses of the queues and if they contain the POISON object.
            System.out.println(ConsoleColors.WHITE +
                    "POISON |" +
                    " [Foyer size: " + foyer.size() +
                    " , Foyer has POISON? " + foyer.contains(POISON) +
                    "] | [Ticket Machine Q size: " + ticketMachineQ.size() +
                    " , Ticket Machine Q has POISON? " + ticketMachineQ.contains(POISON) +
                    "] | [Ticket Booth A Q size: " + ticketBoothAQ.size() +
                    " , Ticket Booth A has POISON? " + ticketBoothAQ.contains(POISON) +
                    "] | [Ticket Booth B Q size: " + ticketBoothBQ.size() +
                    " , Ticket Booth B has POISON? " + ticketBoothBQ.contains(POISON) +
                    "]" +
                    ConsoleColors.RESET);
            System.out.println("POISON | poisonThread on " + Thread.currentThread().getName() + " has end.");
        });
        //Starts thread.
        poisonThread.start();
        //Prints out to console.
        System.out.println(ConsoleColors.WHITE_BOLD + "POISON | poisonThread on " + poisonThread.getName() + " started." + ConsoleColors.RESET);

        //Prints out to console stating that the simulation has started
        System.out.println(ConsoleColors.BLACK_BACKGROUND + "ASIA PACIFIC BUS TERMINAL SIMULATION HAS STARTED!" + ConsoleColors.RESET);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
     * METHODS
     * */
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * In Milliseconds, Sleeps the thread.
     *
     * @param i = in Milliseconds passed to sleep
     */
    private void delay(int i) {
        try {
            TimeUnit.MILLISECONDS.sleep(i);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * In Milliseconds, Sleeps the thread from a selected random period of time between X and Y.
     *
     * @param x = in Milliseconds passed to sleep from.
     * @param y = in Milliseconds passed to sleep to.
     */
    private void delayRandomlyBetweenXToYMilliseconds(int x, int y) {
        try {
            TimeUnit.MILLISECONDS.sleep(x + ThreadLocalRandom.current().nextInt(y));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * In Seconds, Sleeps the thread from a selected random period of time between X and Y.
     *
     * @param x = in Seconds passed to sleep from.
     * @param y = in Seconds passed to sleep from.
     */
    private void delayRandomlyBetweenXtoYSeconds(int x, int y) {
        try {
            TimeUnit.SECONDS.sleep(x + ThreadLocalRandom.current().nextInt(y));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
