import java.io.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.time.Duration;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;

public class LogParser {

    public static void main(String[] args) throws FileNotFoundException, IOException {
        if (args.length != 1) {
            System.err.println("usage: java LogParser logFilePath");
            return;
        }
        String file = args[0];

        Map<Integer, long[]> users = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            for (String line; (line = br.readLine()) != null;) {
                String[] splitted = line.toLowerCase().split(", ");
                Long time = Long.valueOf(splitted[0]);
                Integer id = Integer.valueOf(splitted[1]);
                updateUser(id, time, splitted[2], users);
            }
        }

        Stream<SimpleEntry<Integer, Duration>> stats = users
                .entrySet()
                .stream()
                .sorted((a, b) -> -Long.compare(a.getValue()[0], b.getValue()[0]))
                .map(u -> new SimpleEntry<Integer, Duration>(u.getKey(), Duration.ofMillis(u.getValue()[0])));

        print(stats);
    }

    private static void updateUser(Integer id, long time, String event, Map<Integer, long[]> users) {

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

    private static void print(Stream<SimpleEntry<Integer, Duration>> stats) {
        int lineCount = 0;
        final int lineMaxCount = 30;

        for (SimpleEntry<Integer, Duration> user : stats.collect(toList())) {
            if (lineCount >= lineMaxCount) {
                lineCount = 0;
                System.out.printf("Next %d rows? press 'Enter'. Exit? press 'q' ", lineMaxCount);
                Scanner scanner = new Scanner(System.in);
                String key = scanner.nextLine();
                if ("q".equals(key)) {
                    break;
                }
            }
            ++lineCount;
            Duration duration = user.getValue();
            System.out.printf("(userId = %d): %d.%02d:%02d:%02d\n", user.getKey(),
                    duration.toDays(),
                    duration.toHours() % 24,
                    duration.toMinutes() % 60,
                    duration.getSeconds() % 60);
        }
    }
}
