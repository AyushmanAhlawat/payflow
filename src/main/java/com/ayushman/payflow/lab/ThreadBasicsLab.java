package com.ayushman.payflow.lab;

/*
 * FILE SUMMARY: ThreadBasicsLab
 * Why it exists : Playground to see threads behave before Spring hides them.
 *                 Deleting it breaks nothing in the app.
 * What it does  : Creates 3 threads in 3 ways, starts them, waits with join(),
 *                 and prints which thread runs each line.
 * Who calls it  : Nobody. Run its main() directly. Uses no other PayFlow file.
 * Data it holds : Nothing. No fields; only local variables that vanish when main() ends.
 * Thread-safety : The threads share no data, so nothing needs protecting yet.
 */



public class ThreadBasicsLab {

    // The work every thread does: count to 5 and say which thread is counting.
    private static void countToFive(){
        String name =  Thread.currentThread().getName();
        for (int i = 1; i <= 5; i++){
            System.out.println(name + " -> count " + i);
        }
    }

    // WAY 1: extend Thread and override run().
    static class  CounterThread extends Thread{
        CounterThread(String name){
            super(name);
        }
        @Override
        public void run(){
            countToFive();
        }
    }

    // WAY 2: implement Runnable (the task), then hand it to a Thread (the worker).
    static class CounterTask implements Runnable {
        @Override
        public void run(){
            countToFive();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("main starts on thread: " + Thread.currentThread().getName());

        Thread t1 = new CounterThread("thread-A-extends");
        Thread t2 = new Thread(new CounterTask(), "thread-B-implements");
        Thread t3 = new Thread(() -> countToFive(), "thread-C-lambda");

        System.out.println("t1 state before starts: " + t1.getState());
        t1.start(); t2.start(); t3.start();
        t1.join(); t2.join(); t3.join();
        System.out.println("t1 state after join: " + t1.getState());
        System.out.println("main ends: all 3 thread finished");

    }


}
