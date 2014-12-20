
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * @author Andrey Ivanov
 */
public class NIODaemon {

    private static final List<SocketChannel> socketChannels = new CopyOnWriteArrayList<>();
    private static final BlockingQueue<Response> responseQueue = new LinkedBlockingQueue<>();
    private static final int BUFFER_SIZE = 2 << 13;
    private static final int TIMEOUT = 1000; //1c

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: java NIODaemon port");
            return;
        }
        final int port = Integer.parseInt(args[0]);
        System.out.println("Starting...");

        Server server = new Server(port);
        new Thread(server).start();
        new Thread(new Sender()).start();
    }

    private static void closeChannel(SocketChannel socketChannel) {
        if (socketChannel != null) {
            try {
                socketChannel.close();
            } catch (IOException e) {
                System.err.println(e);
            }
            socketChannels.remove(socketChannel);
        }
    }

    private static final class Server implements Runnable {

        private final ServerSocketChannel serverSocket;
        private final Selector selector;
        private final ByteBuffer inputBuffer;

        public Server(int port) throws IOException {
            this.inputBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            this.serverSocket = ServerSocketChannel.open();
            this.serverSocket.socket().bind(new InetSocketAddress(port));
            this.serverSocket.configureBlocking(false);
            this.selector = Selector.open();
            this.serverSocket.register(this.selector, SelectionKey.OP_ACCEPT);
        }

        @Override
        public void run() {
            System.out.println("Server started on port: "
                    + this.serverSocket.socket().getLocalPort());

            while (!Thread.interrupted()) {
                try {
                    int ready = this.selector.select(TIMEOUT);
                    if (ready == 0) {
                        continue;
                    }

                    Iterator<SelectionKey> selectedKeys
                            = this.selector.selectedKeys().iterator();
                    while (selectedKeys.hasNext()) {
                        SelectionKey key = selectedKeys.next();
                        selectedKeys.remove();

                        if (!key.isValid()) {
                            System.err.println("Key is not valid " + key);
                        } else if (key.isAcceptable()) {
                            this.accept(key);
                        } else if (key.isReadable()) {
                            this.read(key);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void accept(SelectionKey key) throws IOException {
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
            SocketChannel socketChannel = serverSocketChannel.accept();
            if (socketChannel != null) {
                socketChannel.configureBlocking(false);
                socketChannel.register(this.selector, SelectionKey.OP_READ);
                socketChannels.add(socketChannel);
                System.out.printf("Connected %s\n", socketChannel.getRemoteAddress());
            }
        }

        private void read(SelectionKey key) {
            this.inputBuffer.clear();
            SocketChannel socketChannel = (SocketChannel) key.channel();
            String address;
            try {
                address = socketChannel.getRemoteAddress().toString();
                int byteCount = socketChannel.read(this.inputBuffer);
                this.inputBuffer.flip();
                if (byteCount == -1) {
                    key.cancel();
                    closeChannel(socketChannel);
                }
            } catch (IOException e) {
                key.cancel();
                closeChannel(socketChannel);
                this.inputBuffer.clear();
                e.printStackTrace();
                return;
            }
            if (this.inputBuffer.limit() > 0) {
                byte[] data = new byte[this.inputBuffer.limit()];
                this.inputBuffer.get(data);
                responseQueue.add(new Response(address, data));
            }
            this.inputBuffer.clear();
        }
    }

    private static final class Sender implements Runnable {

        private final ByteBuffer outputBuffer;

        public Sender() {
            this.outputBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    Response response = responseQueue.take();
                    socketChannels.stream().forEach(socketChannel -> {
                        if (!socketChannel.isConnected()) {
                            closeChannel(socketChannel);
                            return;
                        }
                        try {
                            if (!socketChannel.getRemoteAddress().toString()
                                    .equals(response.getInitiator())) {
                                this.write(socketChannel, response);
                            }
                        } catch (Exception e) {
                            closeChannel(socketChannel);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void write(SocketChannel socketChannel,
                final Response response) throws IOException {

            this.outputBuffer.clear();
            this.outputBuffer.put(response.getData());
            this.outputBuffer.flip();
            socketChannel.write(this.outputBuffer);
        }
    }

    private static class Response {

        private final String initiator;
        private final byte[] data;

        public Response(String initiator, byte[] data) {
            this.initiator = initiator;
            this.data = data;
        }

        public String getInitiator() {
            return this.initiator;
        }

        public byte[] getData() {
            return data;
        }
    }
}
