package ir.bita.esb.ws;

/**
 * Simple implementation of PingService for tests.
 */
public class PingServiceImpl implements PingService {

    @Override
    public String ping(String message) {
        return "pong: " + message;
    }
}
