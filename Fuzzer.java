import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fuzzer {

    private static final Random random = new Random();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Fuzzer.java \"<command_to_fuzz>\"");
            System.exit(1);
        }
        String commandToFuzz = args[0];
        String workingDirectory = "./";

        // Seed input for mutations
        String seedInput = "<html a=\"value\">...</html>";

        // List of mutators (functions that modify the input)
        List<Function<String, String>> mutators = List.of(
                Fuzzer::deleteRandomCharacter,
                Fuzzer::insertRandomCharacter,
                Fuzzer::flipRandomCharacter
        );

        // Set up the process builder for the target command
        ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
        System.out.printf("Command: %s\n", builder.command());

        // Generate mutated inputs based on the seed
        List<String> mutatedInputs = generateMutatedInputs(seedInput, mutators, 10);

        // Run the target command with the seed and mutated inputs
        runCommand(builder, seedInput, mutatedInputs);
    }

    private static ProcessBuilder getProcessBuilderForCommand(String command, String workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }
        builder.directory(new File(workingDirectory));
        builder.redirectErrorStream(true); // Redirect stderr to stdout for unified output
        return builder;
    }

    private static void runCommand(ProcessBuilder builder, String seedInput, List<String> mutatedInputs) {
        // Combine seed input and mutated inputs for processing
        Stream.concat(Stream.of(seedInput), mutatedInputs.stream()).forEach(
                input -> {
                    try {
                        // Start the process
                        Process process = builder.start();

                        // Write input to the process
                        OutputStream processInput = process.getOutputStream();
                        processInput.write(input.getBytes());
                        processInput.close();

                        // Capture and print the process output
                        String output = readStreamIntoString(process.getInputStream());
                        System.out.printf("Input: %s\nOutput: %s\n", input, output);

                        // Wait for the process to complete
                        process.waitFor();
                    } catch (IOException | InterruptedException e) {
                        System.err.printf("Error executing command with input: %s\n", input);
                        e.printStackTrace();
                    }
                }
        );
    }

    private static String readStreamIntoString(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines()
                .map(line -> line + System.lineSeparator())
                .collect(Collectors.joining());
    }

    private static List<String> generateMutatedInputs(String seedInput, List<Function<String, String>> mutators, int count) {
        List<String> mutatedInputs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            mutatedInputs.add(applyRandomMutation(seedInput, mutators));
        }
        return mutatedInputs;
    }

    private static String applyRandomMutation(String input, List<Function<String, String>> mutators) {
        // Select a random mutator and apply it to the input
        Function<String, String> mutator = mutators.get(random.nextInt(mutators.size()));
        return mutator.apply(input);
    }

    // Mutation functions

    private static String deleteRandomCharacter(String s) {
        if (s.isEmpty()) return s;
        int pos = random.nextInt(s.length());
        return s.substring(0, pos) + s.substring(pos + 1);
    }

    private static String insertRandomCharacter(String s) {
        int pos = random.nextInt(s.length() + 1);
        char randomChar = (char) (random.nextInt(95) + 32); // Printable ASCII (32–126)
        return s.substring(0, pos) + randomChar + s.substring(pos);
    }

    private static String flipRandomCharacter(String s) {
        if (s.isEmpty()) return s;
        int pos = random.nextInt(s.length());
        char c = s.charAt(pos);
        int bit = 1 << random.nextInt(7); // Flip one of the 7 bits
        char newChar = (char) (c ^ bit);
        return s.substring(0, pos) + newChar + s.substring(pos + 1);
    }
}
