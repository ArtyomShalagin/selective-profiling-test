package org.jetbrains.test;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public class Reader {
    public static void main(String[] args) {
        File f = Paths.get("data.txt").toFile();
        if (!f.exists()) {
            System.err.println("run Main to generate call trees first");
            return;
        }
        List<CallTree> trees;
        try {
            trees = CallTree.parse(f);
        } catch (IOException e) {
            System.err.println("unable to parse trees from file");
            return;
        }
        trees.forEach(System.out::println);

        List<CallTree> treesSerialized = new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(new File("data.ser")))) {
            while (true) {
                try {
                    treesSerialized.add((CallTree) ois.readObject());
                } catch (EOFException e) {
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("unable to deserialize: " + e.getMessage());
        }

        final boolean[] ok = {trees.size() == treesSerialized.size()};
        if (!ok[0]) {
            System.err.format("amount of trees parsed from text = %d and deserialized = %d",
                    trees.size(), treesSerialized.size());
            return;
        }
        IntStream.range(0, trees.size()).forEach(i ->
                ok[0] |= Objects.equals(trees.get(i), treesSerialized.get(i)));
        if (ok[0]) {
            System.out.println("well done, everything is fine");
        } else {
            System.out.println("oh no, parsed and deserialized trees are different");
        }

    }
}
