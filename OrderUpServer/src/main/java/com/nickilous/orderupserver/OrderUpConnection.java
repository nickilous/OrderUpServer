package com.nickilous.orderupserver;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.nickilous.OrderMessage;
import com.nickilous.OrderInfo;

import org.apache.http.conn.util.InetAddressUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by Nick on 10/17/13.
 */
public class OrderUpConnection {
    private Handler mUpdateHandler;
    private OrderUpServer mOrderUpServer;
    private OrderUpClient mOrderUpClient;

    public static int CONNECTED = 0;

    private static final String TAG = "OrderUpConnection";

    private Socket mSocket;
    private int mPort = -1;

    final private int PORT_NUMBER = 5050;

    public OrderUpConnection(Handler handler) {
        mUpdateHandler = handler;
        mOrderUpServer = new OrderUpServer(handler);
    }

    public void tearDown() {
        if(mOrderUpServer != null) mOrderUpServer.tearDown();
        if(mOrderUpClient != null) mOrderUpClient.tearDown();
    }

    public void connectToServer(InetAddress address, int port) {
        mOrderUpClient = new OrderUpClient(address, port);
    }


    public int getLocalPort() {
        return mPort;
    }

    public void setLocalPort(int port) {
        mPort = port;
    }


    public synchronized void updateMessages(OrderMessage msg) {
        Log.e(TAG, "Updating message: " + msg);


        Bundle messageBundle = new Bundle();
        messageBundle.putSerializable("orderMessage", msg);

        Message message = new Message();
        message.arg1 = msg.getAction();
        message.setData(messageBundle);
        mUpdateHandler.sendMessage(message);

    }

    private synchronized void setSocket(Socket socket) {
        Log.d(TAG, "setSocket being called.");
        if (socket == null) {
            Log.d(TAG, "Setting a null socket.");
        }
        if (mSocket != null) {
            if (mSocket.isConnected()) {
                try {
                    mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        mSocket = socket;
    }

    private Socket getSocket() {
        return mSocket;
    }

    private class OrderUpServer {
        ServerSocket mServerSocket = null;
        Thread mThread = null;

        public OrderUpServer(Handler handler) {
            mThread = new Thread(new ServerThread());
            mThread.start();
        }

        public void tearDown() {
            mThread.interrupt();
            try {
                mServerSocket.close();
            } catch (IOException ioe) {
                Log.e(TAG, "Error when closing server socket.");
            }
        }

        class ServerThread implements Runnable {

            @Override
            public void run() {

                try {

                    mServerSocket = new ServerSocket(PORT_NUMBER);
                    setLocalPort(mServerSocket.getLocalPort());

                    while (!Thread.currentThread().isInterrupted()) {
                        Log.d(TAG, "ServerSocket Created, awaiting connection");
                        setSocket(mServerSocket.accept());
                        Log.d(TAG, "Connected.");
                        Bundle messageBundle = new Bundle();
                        Message message = new Message();
                        message.arg1 = CONNECTED;
                        message.setData(messageBundle);
                        mUpdateHandler.sendMessage(message);
                        if (mOrderUpClient == null) {
                            int port = mSocket.getPort();
                            InetAddress address = mSocket.getInetAddress();
                            connectToServer(address, port);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error creating ServerSocket: ", e);
                    e.printStackTrace();
                }
            }
        }
    }

    private class OrderUpClient {

        private InetAddress mAddress;
        private int PORT;

        private final String CLIENT_TAG = "OrderUpClient";

        private Thread mSendThread;
        private Thread mRecThread;

        public OrderUpClient(InetAddress address, int port) {

            Log.d(CLIENT_TAG, "Creating OrderUpClient");
            this.mAddress = address;
            this.PORT = port;

            mSendThread = new Thread(new SendingThread());
            mSendThread.start();
        }

        class SendingThread implements Runnable {

            BlockingQueue<String> mMessageQueue;
            private int QUEUE_CAPACITY = 10;

            public SendingThread() {
                mMessageQueue = new ArrayBlockingQueue<String>(QUEUE_CAPACITY);
            }

            @Override
            public void run() {
                try {
                    if (getSocket() == null) {
                        setSocket(new Socket(mAddress, PORT));
                        Log.d(CLIENT_TAG, "Client-side socket initialized.");

                    } else {
                        Log.d(CLIENT_TAG, "Socket already initialized. skipping!");
                    }

                    mRecThread = new Thread(new ReceivingThread());
                    mRecThread.start();

                } catch (UnknownHostException e) {
                    Log.d(CLIENT_TAG, "Initializing socket failed, UHE", e);
                } catch (IOException e) {
                    Log.d(CLIENT_TAG, "Initializing socket failed, IOE.", e);
                }

                while (true) {
                    try {
                        String msg = mMessageQueue.take();
                        //sendMessage(msg);
                    } catch (InterruptedException ie) {
                        Log.d(CLIENT_TAG, "Message sending loop interrupted, exiting");
                    }
                }
            }
        }

        class ReceivingThread implements Runnable {
            OrderMessage mOrderMessage;
            @Override
            public void run() {

                BufferedReader input;
                try {
                    input = new BufferedReader(new InputStreamReader(
                            mSocket.getInputStream()));
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            ObjectInputStream inputStream = new ObjectInputStream(getSocket().getInputStream());
                            mOrderMessage = (OrderMessage) inputStream.readObject();
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }

                        if (mOrderMessage != null) {
                            Log.d(CLIENT_TAG, "Read from the stream: " + mOrderMessage.toString());
                            updateMessages(mOrderMessage);
                        } else {
                            Log.d(CLIENT_TAG, "Order message was null");
                            break;
                        }
                    }
                    input.close();

                } catch (IOException e) {
                    Log.e(CLIENT_TAG, "Server loop error: ", e);
                }
            }
        }

        public void tearDown() {
            try {
                getSocket().close();
            } catch (IOException ioe) {
                Log.e(CLIENT_TAG, "Error when closing server socket.");
            }
        }

        public void sendMessage(OrderMessage msg) {
            try {
                Socket socket = getSocket();
                if (socket == null) {
                    Log.d(CLIENT_TAG, "Socket is null, wtf?");
                } else if (socket.getOutputStream() == null) {
                    Log.d(CLIENT_TAG, "Socket output stream is null, wtf?");
                }

                PrintWriter out = new PrintWriter(
                        new BufferedWriter(
                                new OutputStreamWriter(getSocket().getOutputStream())), true);
                out.println(msg);
                out.flush();
                updateMessages(msg);
            } catch (UnknownHostException e) {
                Log.d(CLIENT_TAG, "Unknown Host", e);
            } catch (IOException e) {
                Log.d(CLIENT_TAG, "I/O Exception", e);
            } catch (Exception e) {
                Log.d(CLIENT_TAG, "Error3", e);
            }
            Log.d(CLIENT_TAG, "Client sent message: " + msg);
        }
    }



    /**
     * Get IP address from first non-localhost interface
     *
     * @param useIPv4 true=return ipv4, false=return ipv6
     * @return address or empty string
     */
    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress().toUpperCase();
                        boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 port suffix
                                return delim < 0 ? sAddr : sAddr.substring(0, delim);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
        } // for now eat exceptions
        return "";
    }
}
