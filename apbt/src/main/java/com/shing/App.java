package com.shing;

import com.shing.Runnables.Terminal;

public class App {
    public static void main(String[] args) {
        Thread simulation = new Thread(new Terminal());
        simulation.start();
    }
}
