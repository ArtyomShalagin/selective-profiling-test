package org.jetbrains.test;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Class for methods profiling.
 * Usage:
 * <pre>
 * <code>try (CallTree.Overseer ignored = callTree.stepIn(s)) {
 *     // code here
 * } </code></pre>
 * CallTree supports writing and parsing the data in human-readable format
 * with {@link #write(OutputStream)} and {@link #parse(File)}.
 * Serialization is also supported.
 * <p>
 * Iteration over CallTree elements goes in order of execution.
 */
public class CallTree implements Serializable, Iterable<CallTree.CallTreeEntry> {
    private final CallTreeEntry root;
    private CallTreeEntry current;
    private int hash = 56630239;

    private static final String ROOT_IDENT = "entry_point";

    public CallTree() {
        root = new CallTreeEntry(0, null, null, null, null);
        current = root;
    }

    private CallTree(CallTreeEntry root) {
        this.root = root;
        for (CallTreeEntry entry : this) {
            current = entry;
        }
    }

    public Overseer stepIn(Object... args) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // index = 2 because of 2 calls we don't want to log -- getStackTrace and stepIn
        StackTraceElement elem = stackTrace[2];
        List<Class<?>> argsTypes = Arrays.stream(args)
                .map(o -> o == null ? null : o.getClass())
                .collect(Collectors.toList());
        Class<?> caller = null;
        Method method = null;
        Overseer overseer = new Overseer(this);
        try {
            try {
                caller = Class.forName(elem.getClassName());
            } catch (ClassNotFoundException e) {
                System.err.println("Unable to load caller class: " + e.getMessage());
                return overseer;
            }
            // seems like getDeclaredMethods is enough and we don't need to look up
            // methods recursively because we can see the actual caller in the call stack
            method = findMethod(caller.getDeclaredMethods(), elem.getMethodName(), argsTypes);
            if (method == null) {
                System.err.println("Unable to find caller method " + elem.getMethodName());
            }
        } finally {
            // any errors in the code above are very unlikely to happen but even if they
            // did happen we need to keep the log balanced in order not to ruin
            // all log messages in the future
            current = current.add(caller, method,
                    Arrays.stream(args)
                            .map(Objects::toString)
                            .toArray(String[]::new));
            hash = hash * 31 + current.hashCode();
        }
        return overseer;
    }

    public void write(OutputStream os) throws IOException {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        for (CallTreeEntry entry : this) {
            out.println(entry);
        }
        out.println();
        out.flush();
    }

    public static List<CallTree> parse(File f) throws IOException {
        InputStream is = new FileInputStream(f);
        Scanner in = new Scanner(is);
        List<CallTree> data = new ArrayList<>();
        Optional<CallTree> next;
        while ((next = CallTreeParser.parseNext(in)).isPresent()) {
            data.add(next.get());
        }
        in.close();
        return data;
    }

    private Method findMethod(Method[] methods, String name, List<Class<?>> argsTypes) {
        for (Method m : methods) {
            if (m.getParameterCount() != argsTypes.size() || !Objects.equals(m.getName(), name)) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            // even though there might be null args and we can't get their type tokens
            // we still get no ambiguities here
            // we also need unwrapping here because varargs in stepIn box primitives
            if (IntStream.range(0, params.length)
                    .allMatch(i -> argsTypes.get(i) == null || argsTypes.get(i).equals(Util.unwrap(params[i])))) {
                return m;
            }
        }
        return null;
    }

    private void stepOut() {
        current = current.getParent();
        int size = current.getChildren().size();
        hash = hash * current.getChildren().get(size - 1).hashCode() * size * 57;
    }

    @Override
    public Iterator<CallTreeEntry> iterator() {
        return new CallTreeIterator(root);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CallTree && equals((CallTree) other);
    }

    private boolean equals(CallTree other) {
        return root.deepEquals(other.root);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    public String toString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            write(baos);
            return baos.toString();
        } catch (IOException e) {
            return "object is corrupted";
        }
    }

    public class Overseer implements AutoCloseable {
        private CallTree parent;

        private Overseer(CallTree parent) {
            this.parent = parent;
        }

        @Override
        public void close() {
            parent.stepOut();
        }
    }

    // storing a tree is a bit more difficult than a linked list
    // however, getChildren method seems quite useful
    public static class CallTreeEntry implements Serializable {
        private final int depth;
        private final Class<?> caller;
        private transient Method method;
        // we can't store objects here because there is no way to restore an object
        // from its toString(). serialization obviously wouldn't work because we
        // may want to pass arguments that are not serializable.
        private final String[] args;

        private final List<CallTreeEntry> children = new ArrayList<>();
        private CallTreeEntry parent;

        CallTreeEntry(int depth, CallTreeEntry parent, Class<?> caller, Method method, String[] args) {
            this.depth = depth;
            this.parent = parent;
            this.caller = caller;
            this.method = method;
            this.args = args;
        }

        CallTreeEntry add(Class<?> caller, Method method, String[] args) {
            CallTreeEntry child = new CallTreeEntry(depth + 1, this, caller, method, args);
            children.add(child);
            return child;
        }

        CallTreeEntry getParent() {
            return parent;
        }

        public Class<?> getCaller() {
            return caller;
        }

        public Method getMethod() {
            return method;
        }

        public String[] getArgs() {
            return args;
        }

        public List<CallTreeEntry> getChildren() {
            return children;
        }

        public int getDepth() {
            return depth;
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            try {
                method = Util.getMethod(caller, (String) in.readObject(), (Class<?>[]) in.readObject());
            } catch (NoSuchMethodException e) {
                System.err.println("unable to restore Method from method info during deserialization");
            }
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeObject(method == null ? null : method.getName());
            out.writeObject(method == null ? null : method.getParameterTypes());
        }

        // this checks only data equality
        @Override
        public boolean equals(Object other) {
            return other instanceof CallTreeEntry && equals((CallTreeEntry) other);
        }

        private boolean equals(CallTreeEntry other) {
            return depth == other.depth && Objects.equals(caller, other.caller)
                    && Objects.equals(method, other.method) && Arrays.deepEquals(args, other.args);
        }

        private boolean deepEquals(CallTreeEntry other) {
            boolean ok = equals(other) && getChildren().size() == other.getChildren().size();
            if (!ok) {
                return false;
            }
            for (int i = 0; i < getChildren().size(); i++) {
                ok &= getChildren().get(i).deepEquals(other.getChildren().get(i));
            }
            return ok;
        }

        @Override
        public int hashCode() {
            return Objects.hash(depth, caller, method) ^ Arrays.hashCode(args);
        }

        @Override
        public String toString() {
            if (parent == null) {
                return ROOT_IDENT;
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(String.join("", Collections.nCopies(depth, "-")));
                sb.append(caller.getName()).append('.').append(method.getName()).append('(');
                Class<?>[] params = method.getParameterTypes();
                String argsStr = IntStream.range(0, params.length)
                        .mapToObj(i -> String.format("%s var%d = %s", params[i].getName(), i, args[i]))
                        .collect(Collectors.joining(", "));
                sb.append(argsStr).append(")");
                return sb.toString();
            }
        }
    }

    private static class CallTreeIterator implements Iterator<CallTreeEntry> {
        private CallTreeEntry curr;
        private Stack<Integer> indices;

        CallTreeIterator(CallTreeEntry root) {
            curr = root;
            indices = new Stack<>();
            indices.push(0);
        }

        @Override
        public boolean hasNext() {
            return curr != null;
        }

        @Override
        public CallTreeEntry next() {
            CallTreeEntry next = curr;
            while (indices.size() != 0 && indices.peek() == curr.getChildren().size()) {
                indices.pop();
                curr = curr.getParent();
            }
            if (indices.size() == 0) {
                curr = null;
            } else {
                curr = curr.getChildren().get(indices.peek());
                indices.push(indices.pop() + 1);
                indices.push(0);
            }

            return next;
        }
    }

    // Code here is quite ugly because parsing a human-readable file usually is not much fun
    // but readability is worth it.
    private static class CallTreeParser {
        static Optional<CallTree> parseNext(Scanner in) {
            List<String> data = readData(in);
            if (data.size() == 0) {
                return Optional.empty();
            }
            List<CallTreeEntry> entries = data.stream()
                    .map(CallTreeParser::parseEntry)
                    .collect(Collectors.toList());
            if (entries.contains(null)) {
                System.err.println("error in parser, probably file is corrupted or some classes are missing");
                return Optional.empty();
            }
            Stack<CallTreeEntry> stack = new Stack<>();
            for (CallTreeEntry entry : entries) {
                if (stack.size() == 0) {
                    stack.push(entry);
                    continue;
                }
                while (stack.size() > entry.depth) {
                    stack.pop();
                }
                CallTreeEntry curr = stack.peek();
                curr.getChildren().add(entry);
                entry.parent = curr;
                stack.push(entry);
            }
            return Optional.of(new CallTree(entries.get(0)));
        }

        private static List<String> readData(Scanner in) {
            List<String> data = new ArrayList<>();
            String line;
            // caret return is a separator between different call trees in one file
            // which is quite bad but it is done that way for sake of readability
            while (in.hasNextLine() && !Objects.equals(line = in.nextLine(), "")) {
                data.add(line);
            }
            return data;
        }

        private static CallTreeEntry parseEntry(String s) {
            if (s.equals(ROOT_IDENT)) {
                return new CallTreeEntry(0, null, null, null, null);
            }
            int p = 0;
            while (s.charAt(p) == '-') {
                p++;
            }
            int depth = p;
            s = s.substring(p);
            // seems like regex will make that even less readable
            String semantics = s.substring(0, s.indexOf('('));
            String callerName = semantics.substring(0, semantics.lastIndexOf('.'));
            Class<?> caller;
            try {
                caller = Class.forName(callerName);
            } catch (ClassNotFoundException e) {
                System.err.println("parser: unable to load class: " + e.getMessage());
                return null;
            }
            String methodName = semantics.substring(semantics.lastIndexOf('.') + 1);
            String paramsStr = s.substring(s.indexOf('(') + 1, s.length() - 1);
            MethodInfo methodInfo = parseMethod(caller, methodName, paramsStr);
            return new CallTreeEntry(depth, null, caller, methodInfo.method, methodInfo.args);
        }

        private static MethodInfo parseMethod(Class<?> caller, String name, String paramsStr) {
            String[] params = paramsStr.equals("") ? new String[0] : paramsStr.split(", ");
            Class<?>[] paramsTypes = new Class<?>[params.length];
            String[] args = new String[params.length];
            IntStream.range(0, params.length).forEach(i -> {
                String argTypeName = params[i].substring(0, params[i].indexOf(' '));
                try {
                    paramsTypes[i] = Util.forName(argTypeName);
                } catch (ClassNotFoundException e) {
                    System.err.println("Unable to load class: " + e.getMessage());
                }
                args[i] = params[i].substring(params[i].indexOf('=') + 2, params[i].length());
            });
            Method method = null;
            try {
                method = caller.getDeclaredMethod(name, paramsTypes);
            } catch (NoSuchMethodException e) {
                System.err.println("Unable to find method: " + e.getMessage());
            }
            return new MethodInfo(method, args);
        }

        private static class MethodInfo {
            Method method;
            String[] args;

            MethodInfo(Method method, String[] args) {
                this.method = method;
                this.args = args;
            }
        }
    }
}
