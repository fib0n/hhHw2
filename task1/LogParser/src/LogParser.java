
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
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
        Map<Integer, long[]> users = new HashMap<>();
        try (ReadableByteChannel channel = Files.newByteChannel(
                Paths.get(args[0]))) {
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1 << 13);
            Charset charset = Charset.forName(System.getProperty("file.encoding"));
            char delim = System.getProperty("line.separator").charAt(0);

            StringBuilder line = new StringBuilder();
            while (channel.read(byteBuffer) > 0) {
                byteBuffer.rewind();
                CharBuffer charBuffer = charset.decode(byteBuffer);
                while (charBuffer.hasRemaining()) {
                    char symbol = charBuffer.get();
                    if (symbol == delim) {
                        updateUser(line.toString(), users);
                        line = new StringBuilder();
                    } else {
                        line.append(symbol);
                    }
                }
                byteBuffer.flip();
            }
            updateUser(line.toString(), users);
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

    private static void updateUser(String data,
            final Map<Integer, long[]> users) {
        if (data == null) {
            return;
        }
        String[] splitted = data.toLowerCase().split(", ");
        if (splitted.length < 3) {
            return;
        }

        Long time = Long.valueOf(splitted[0]);
        Integer id = Integer.valueOf(splitted[1]);
        String event = splitted[2];

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
