
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;

/**
 *
 * @author Andrey Ivanov
 */
public class LogParser {

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: java LogParser logFilePath");
            return;
        }

        String file = args[0];
        Path path = Paths.get(file);
        Map<Integer, long[]> users = new HashMap<>();

        try (Scanner scanner = new Scanner(
                path, StandardCharsets.UTF_8.name())) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] splitted = line.toLowerCase().split(", ");
                Long time = Long.valueOf(splitted[0]);
                Integer id = Integer.valueOf(splitted[1]);
                updateUser(id, time, splitted[2], users);
            }
        }

        Stream<SimpleEntry<Integer, Duration>> stats = users
                .entrySet()
                .stream()
                .sorted((a, b) -> -Long.compare(
                                a.getValue()[0], b.getValue()[0]))
                .map(u -> new SimpleEntry<Integer, Duration>(
                                u.getKey(),
                                Duration.ofSeconds(u.getValue()[0])));
        print(stats);
    }

    private static void updateUser(final Integer id,
            final long time, final String event,
            final Map<Integer, long[]> users) {

        if (!users.containsKey(id)) {
            users.put(id, new long[]{0L, 0L});
        }
        long[] userData = users.get(id);

        switch (event) {
            case "login":
                userData[1] = time;
                break;
            case "logout":
                userData[0] += time - userData[1];
                userData[1] = 0;
                break;
        }
    }

    private static void print(
            final Stream<SimpleEntry<Integer, Duration>> stats) {
        int lineCount = 0;
        final int lineMaxCount = 30;

        for (SimpleEntry<Integer, Duration> user : stats.collect(toList())) {
            if (lineCount >= lineMaxCount) {
                lineCount = 0;
                System.out.printf(
                        "Next %d rows? press 'Enter'. Exit? press 'q' ",
                        lineMaxCount);
                Scanner scanner = new Scanner(System.in);
                String key = scanner.nextLine();
                if ("q".equals(key)) {
                    break;
                }
            }
            ++lineCount;
            Duration duration = user.getValue();
            System.out.printf("(userId = %d): %d.%02d:%02d:%02d\n",
                    user.getKey(),
                    duration.toDays(),
                    duration.toHours() % 24,
                    duration.toMinutes() % 60,
                    duration.getSeconds() % 60);
        }
    }
}
