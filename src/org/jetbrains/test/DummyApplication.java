package org.jetbrains.test;

import java.util.List;
import java.util.Random;

/**
 * Nikolay.Tropin
 * 18-Apr-17
 */
public class DummyApplication {
    private final List<String> args;
    private Random random = new Random(System.nanoTime());
    CallTree callTree = new CallTree();

    public DummyApplication(List<String> args) {
        this.args = args;
    }

    private boolean nextBoolean() {
        return random.nextBoolean();
    }

    private boolean stop() {
        return random.nextDouble() < 0.05;
    }

    private String nextArg() {
        int idx = random.nextInt(args.size());
        return args.get(idx);
    }

    private void sleep() {
        try {
            Thread.sleep(random.nextInt(20));
        } catch (InterruptedException ignored) {

        }
    }

    private void abc(String s) {
        try (CallTree.Overseer ignored = callTree.stepIn(s)) {
            sleep();
            if (stop()) {
                //do nothing
            } else if (nextBoolean()) {
                def(nextArg());
            } else {
                xyz(nextArg());
            }
        }
    }

    private void def(String s) {
        try (CallTree.Overseer ignored = callTree.stepIn(s)) {
            sleep();
            if (stop()) {
                //do nothing
            } else if (nextBoolean()) {
                abc(nextArg());
            } else {
                xyz(nextArg());
            }
        }
    }

    private void xyz(String s) {
        try (CallTree.Overseer ignored = callTree.stepIn(s)) {
            sleep();
            if (stop()) {
                //do nothing
            } else if (nextBoolean()) {
                abc(nextArg());
            } else {
                def(nextArg());
            }
        }
    }

    public void start() {
        abc(nextArg());
    }
}
