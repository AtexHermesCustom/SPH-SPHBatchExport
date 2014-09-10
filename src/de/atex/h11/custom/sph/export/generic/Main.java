/*
 * File:    Main.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  25-apr-2012  st  Initial version.
 * v00.00  14-feb-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.generic;

import java.io.FileInputStream;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class of the batch export.
 * @author tstuehler
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        LinkedBlockingQueue<QueueElement> workq = null;
        LinkedBlockingQueue<QueueElement> dumpq = null;
        Properties props = new Properties();

        logger.entering(loggerName, "main");

        try {
            /* Gather command line parameters.
             * p - properties file
             * o - output option
             */
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-p"))
                    props.load(new FileInputStream(args[++i]));
                else if (args[i].startsWith("-p"))
                    props.load(new FileInputStream(args[i].substring(2)));
                else if (args[i].equals("-o"))
                    props.setProperty("outputOption", args[++i]);
                else if (args[i].startsWith("-o"))
                    props.setProperty("outputOption", args[i].substring(2));
            }
            
            // Get the number of wrokers to start.
            int numWorkers = Integer.parseInt(props.getProperty("numWorkers",
                                            Integer.toString(DEFAULT_NUMWORKERS)));
            logger.fine("Number of worker threads is " + numWorkers + ".");

            // Get the number of dumpers to start.
            int numDumpers = Integer.parseInt(props.getProperty("numDumpers",
                                            Integer.toString(DEFAULT_NUMDUMPERS)));
            logger.fine("Number of dumper threads is " + numDumpers + ".");

            // Create the worker queue.
            workq = new LinkedBlockingQueue<QueueElement>();

            // Create the dumper queue.
            dumpq = new LinkedBlockingQueue<QueueElement>();


            // Start the dumpers.
            Class dumperClass = Class.forName(
                    System.getProperty("de.atex.h11.custom.sph.export.Dumper", 
                    props.getProperty("dumperClass")));
            Dumper[] dumpers = new Dumper[numDumpers];
            Thread[] dumperThreads = new Thread[numDumpers];
            for (int i = 0; i < dumpers.length; i++) {
                dumpers[i] = (Dumper) dumperClass.newInstance();
                dumpers[i].init(dumpq, props);
                dumperThreads[i] = new Thread(dumpers[i]);
                dumperThreads[i].setName("Dumper-" + i);
                dumperThreads[i].start();
                logger.fine("Dumper thread (" + dumperThreads[i].getId() + ", "
                        + dumperThreads[i].getName() + ") has been started.");
            }

            // Start the workers.
            Class workerClass = Class.forName(
                    System.getProperty("de.atex.h11.custom.sph.export.Worker", 
                    props.getProperty("workerClass")));
            Worker[] workers = new Worker[numWorkers];
            Thread[] workerThreads = new Thread[numWorkers];
            for (int i = 0; i < workers.length; i++) {
                workers[i] = (Worker) workerClass.newInstance();
                workers[i].init(workq, dumpq, props);
                workerThreads[i] = new Thread(workers[i]);
                workerThreads[i].setName("Worker-" + i);
                workerThreads[i].start();
                logger.fine("Worker thread (" + workerThreads[i].getId() + ", "
                        + workerThreads[i].getName() + ") has been started.");
            }

            // Now run the feeder.
            Class feederClass = Class.forName(
                    System.getProperty("de.atex.h11.custom.sph.export.Feeder", 
                    props.getProperty("feederClass")));
            Feeder feeder = (Feeder) feederClass.newInstance();
            feeder.init(workq, props);
            Thread feederThread = new Thread(feeder);
            feederThread.setName("Feeder");
            feederThread.start();
            logger.fine("Feeder thread (" + feederThread.getId() + ", "
                    + feederThread.getName() + ") has been started.");

            // wait until feeder has terminated and workers and dumpers are waiting
            Thread t = Thread.currentThread();
            while (true) {
                if (workq.isEmpty() && dumpq.isEmpty()
                        && feederThread.getState() == Thread.State.TERMINATED) {
                    logger.finer("Queues are empty and feeder has terminated.");
                    boolean bWaiting = true;
                    for (int i = 0; i < dumperThreads.length && bWaiting; i++) {
                        bWaiting = (dumperThreads[i].getState() == Thread.State.TIMED_WAITING
                                || dumperThreads[i].getState() == Thread.State.TERMINATED);
                        logger.finer(dumperThreads[i].getName() + ": thread state is " + dumperThreads[i].getState());
                    }
                    for (int i = 0; i < workerThreads.length && bWaiting; i++) {
                        bWaiting = (workerThreads[i].getState() == Thread.State.TIMED_WAITING
                                || workerThreads[i].getState() == Thread.State.TERMINATED);
                        logger.finer(workerThreads[i].getName() + ": thread state is " + workerThreads[i].getState());
                    }
                    if (bWaiting) break;
                } else {
                    logger.finest("Work queue contains " + workq.size() + " documents.");
                    logger.finest("Dumper queue contains " + dumpq.size() + " documents.");
                    logger.finest(feederThread.getName() + ": thread state is " + feederThread.getState());
                    for (int i = 0; i < workerThreads.length; i++) {
                        logger.finest(workerThreads[i].getName() + ": thread state is " + workerThreads[i].getState());
                        if (workerThreads[i].getState() == Thread.State.TERMINATED) {
                            // Restart a terminated thread, state should be always TIMED_WAITING or RUNNABLE.
                            workers[i] = (Worker) workerClass.newInstance();
                            workers[i].init(workq, dumpq, props);
                            workerThreads[i] = new Thread(workers[i]);
                            workerThreads[i].setName("Worker-" + i);
                            workerThreads[i].start();
                            logger.warning("Worker thread (" + workerThreads[i].getId() + ", "
                                    + workerThreads[i].getName() + ") has been re-started.");
                        }
                    }
                    for (int i = 0; i < dumperThreads.length; i++) {
                        logger.finest(dumperThreads[i].getName() + ": thread state is " + dumperThreads[i].getState());
                        if (dumperThreads[i].getState() == Thread.State.TERMINATED) {
                            dumpers[i] = (Dumper) dumperClass.newInstance();
                            dumpers[i].init(dumpq, props);
                            dumperThreads[i] = new Thread(dumpers[i]);
                            dumperThreads[i].setName("Dumper-" + i);
                            dumperThreads[i].start();
                            logger.warning("Dumper thread (" + dumperThreads[i].getId() + ", "
                                    + dumperThreads[i].getName() + ") has been re-started.");
                        }
                    }
                }
                
                t.sleep(1000);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "", e);
            System.exit(1);
        }

        logger.exiting(loggerName, "main");
        
        System.exit(0);
    }
    
    private static final int DEFAULT_NUMWORKERS = 4;
    private static final int DEFAULT_NUMDUMPERS = 1;

    private static final String loggerName = Main.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);
    
}
