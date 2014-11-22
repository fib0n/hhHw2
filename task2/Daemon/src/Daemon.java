import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.text.*;
import javax.swing.text.html.*;

public class Daemon {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: java Daemon port");
            return;
        }

        int port = Integer.parseInt(args[0]);
        ServerSocket server = null;
        try {
            server = new ServerSocket(port);
            System.out.printf("Initialized on port %d\n", port);
            while (true) {
                Socket socket = server.accept();
                System.out.printf("Connected %s\n", socket.getRemoteSocketAddress());
                Connection conn = new Connection(socket);
                connections.add(conn);
                new Thread(conn).start();
            }
        } catch (IOException e) {
            System.err.println(e);
            if (server != null) {
                server.close();
            }
            connections.stream().forEach(c -> c.close());
        }
    }

    private static final CopyOnWriteArrayList<Connection> connections = new CopyOnWriteArrayList<>();

    private static final class Connection implements Runnable {

        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public Connection(Socket socket) {
            this.socket = socket;

            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                System.err.println(e);
                close();
            }
        }

        @Override
        public void run() {
            try {
                String request = in.readLine();
                String response = getResponse(request);
                connections.stream().forEach(c -> {
                    try {
                        c.out.println(response);
                    } catch (Exception e) {
                        System.err.println(e);
                        c.close();
                    }
                });
            } catch (IOException e) {
                System.err.println(e);
            } finally {
                close();
            }
        }

        public void close() {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
                socket.close();
                connections.remove(this);
            } catch (Exception e) {
                System.err.println(e);
            }
        }

        private static String getResponse(String request) {
            switch (request) {
                //todo more cases
                case "#boobs":
                    return getBoobs();
            }
            return String.format("'%s' from server", request);
        }

        //используется стандартный html-парсер, чтобы не заморачиваться с зависимостями
        public static String getBoobs() {

            try {
                URL url = new URL("http://boobs-selfshots.tumblr.com/page/"
                        + ((int) (Math.random() * 599) + 1));
                HTMLEditorKit kit = new HTMLEditorKit();
                HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
                doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
                try (Reader HTMLReader = new InputStreamReader(url.openConnection().getInputStream())) {
                    kit.read(HTMLReader, doc, 0);

                    ElementIterator it = new ElementIterator(doc);
                    Element elem;

                    while ((elem = it.next()) != null) {
                        if (elem.getName().equals("div")
                                && elem.getAttributes()
                                .getAttribute(HTML.Attribute.CLASS)
                                .equals("photo_post")) {
                            String s = (String) elem.getElement(0).getElement(0)
                                    .getAttributes()
                                    .getAttribute(HTML.Attribute.SRC);
                            if (s != null) {
                                return s;
                            }
                        }
                    }
                }
            } catch (IOException | BadLocationException e) {
                System.err.println(e);
            }
            return "no boobs =(";
        }
    }
}
