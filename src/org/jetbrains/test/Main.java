package org.jetbrains.test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {

    public static void main(String[] args) {
        ExecutorService service = Executors.newFixedThreadPool(3);
        List<DummyApplication> apps = new ArrayList<>();
        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int start = 100 * i;
            List<String> arguments = IntStream.range(start, start + 10)
                    .mapToObj(Integer::toString)
                    .collect(Collectors.toList());
            final DummyApplication app = new DummyApplication(arguments);
            apps.add(app);
            tasks.add(service.submit(app::start));
        }
        service.shutdown();
        for (Future<?> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("exception while waiting for task to execute: " + e.getMessage());
            }
        }
        apps.forEach(app -> System.out.println(app.callTree));

        try (FileOutputStream fos = new FileOutputStream(new File("data.txt"))) {
            for (DummyApplication app : apps) {
                app.callTree.write(fos);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File("data.ser")))) {
            for (DummyApplication app : apps) {
                oos.writeObject(app.callTree);
            }
        } catch (IOException e) {
            System.err.println("can not serialize: " + e.getMessage());
        }
    }
}
