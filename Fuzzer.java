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

        //Seed input for mutations
        String seedInput = "<html a=\"value\">…</html>";

        //List of mutation methods
        List<Function<String, String>> mutators = List.of(
                Fuzzer::deleteRandomCharacter,
                Fuzzer::insertRandomCharacter,
                Fuzzer::flipRandomCharacter,
                Fuzzer::duplicateRandomCharacter,
                Fuzzer::switchRandomCharacterCase
        );

        //Set up the process builder for the target command
        ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
        System.out.printf("Command: %s\n", builder.command());

        //Generate mutated inputs based on the seed
        List<String> mutatedInputs = generateMutatedInputs(seedInput, mutators, 100);

        //Run the command with seed input and mutated inputs
        boolean nonZeroExitCodeFlag = runCommand(builder, seedInput, mutatedInputs);

        //Exit with non-zero code if any run returned a non-zero exit code
        if (nonZeroExitCodeFlag) {
            System.exit(1);
        } else {
            System.exit(0);
        }
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
        builder.redirectErrorStream(true); //Redirect stderr to stdout
        return builder;
    }

    private static boolean runCommand(ProcessBuilder builder, String seedInput, List<String> mutatedInputs) {
        final Boolean[] nonZeroExitCodeFlag = {false}; //Have to use an array as workaround to allow modification inside lambda

        Stream.concat(Stream.of(seedInput), mutatedInputs.stream()).forEach(
                input -> {
                    try {
                        Process process = builder.start();

                        OutputStream processInput = process.getOutputStream();
                        processInput.write(input.getBytes());
                        processInput.close();

                        String output = readStreamIntoString(process.getInputStream());
                        System.out.printf("Input: %s\nOutput: %s\n", input, output);

                        int exitCode = process.waitFor();
                        if (exitCode != 0) {
                            nonZeroExitCodeFlag[0] = true;
                        }
                    } catch (IOException | InterruptedException e) {
                        System.err.printf("Error executing command with input: %s\n", input);
                        e.printStackTrace();
                        nonZeroExitCodeFlag[0] = true;
                    }
                }
        );

        return nonZeroExitCodeFlag[0];
    }


    private static String readStreamIntoString(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines()
                .map(line -> line + System.lineSeparator())
                .collect(Collectors.joining());
    }

    private static List<String> generateMutatedInputs(String seedInput, List<Function<String, String>> mutators, int count) {
        List<String> mutatedInputs = new ArrayList<>();
        String currentInput = seedInput;
        for (int i = 0; i < count; i++) {
            currentInput = applyRandomMutation(currentInput, mutators);
            mutatedInputs.add(currentInput);
        }
        return mutatedInputs;
    }

    private static String applyRandomMutation(String input, List<Function<String, String>> mutators) {
        //Select a random mutation method and apply it to the input
        Function<String, String> mutator = mutators.get(random.nextInt(mutators.size()));
        return mutator.apply(input);
    }

    //Mutation methods

    private static String deleteRandomCharacter(String s) {
        if (s.isEmpty()) return s;
        int pos = random.nextInt(s.length());
        return s.substring(0, pos) + s.substring(pos + 1);
    }

    private static String insertRandomCharacter(String s) {
        int pos = random.nextInt(s.length() + 1);
        char randomChar = (char) (random.nextInt(95) + 32); //Printable ASCII symbols (32–126)
        return s.substring(0, pos) + randomChar + s.substring(pos);
    }

    private static String flipRandomCharacter(String s) {
        if (s.isEmpty()) return s;
        int pos = random.nextInt(s.length());
        char c = s.charAt(pos);
        int bit = 1 << random.nextInt(7); //Flip one of the 7 bits
        char newChar = (char) (c ^ bit);
        return s.substring(0, pos) + newChar + s.substring(pos + 1);
    }

    private static String duplicateRandomCharacter(String s) {
        if (s.isEmpty()) return s;
        int pos = random.nextInt(s.length());
        char c = s.charAt(pos);
        return s.substring(0, pos) + c + s.substring(pos); //Duplicate the character at 'pos'
    }

    private static String switchRandomCharacterCase(String s) {
        if (s.isEmpty()) return s;

        //Check if there are any alphabetic characters in the string
        boolean hasAlphabetic = s.chars().anyMatch(Character::isAlphabetic);
        if (!hasAlphabetic) {
            //If there are no alphabetic characters, return the original string
            return s;
        }

        int pos = random.nextInt(s.length());
        char c = s.charAt(pos);

        //If the character is not a letter, randomly choose another character
        while (!Character.isAlphabetic(c)) {
            pos = random.nextInt(s.length());
            c = s.charAt(pos);
        }

        //Toggle case for alphabetic character
        char newChar = Character.isLowerCase(c) ? Character.toUpperCase(c) : Character.toLowerCase(c);

        return s.substring(0, pos) + newChar + s.substring(pos + 1);
    }
}
