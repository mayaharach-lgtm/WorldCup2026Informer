package bgu.spl.net.impl.stomp;
import bgu.spl.net.srv.Server;

import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: StompServer <port> <tpc/reactor>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String mode = args[1];

        if (mode.equals("tpc")) {
            Server.<StompFrame>threadPerClient(
                    port,
                    () -> new StompMessagingProtocolimpl(), 
                    () -> new StompMessageEncoderDecoder()
            ).serve();
        } 
        else if (mode.equals("reactor")) {
            Server.<StompFrame>reactor(
                    Runtime.getRuntime().availableProcessors(),
                    port,
                    () -> new StompMessagingProtocolimpl(),
                    () -> new StompMessageEncoderDecoder() 
            ).serve();
        } 
        else {
            System.out.println("Unknown mode: " + mode + ". Use 'tpc' or 'reactor'.");
        }
    }
}