package ir.bita.system.support.ws;

import jakarta.jws.WebService;

/**
 * Third-party echo service implementation.
 * Waits {@code time} seconds then returns {@code "I get" + message}.
 * Called directly by CXF via JaxWsServerFactoryBean (POJO dispatch — no Camel involvement).
 */
@WebService(endpointInterface = "ir.bita.system.support.ws.EchoService",
            targetNamespace   = "http://ws.esb.bita.ir/",
            serviceName       = "EchoService")
public class EchoServiceImpl implements EchoService {

    /** How many calls actually reached the provider's business logic. */
    public static final java.util.concurrent.atomic.AtomicInteger CALLS = new java.util.concurrent.atomic.AtomicInteger();


    @Override
    public String echo(String message, int time) {
        CALLS.incrementAndGet();
//        throw new RuntimeException("ajab");
        if (time > 0) {
            try {
                Thread.sleep(time * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        String s = "I get" + message;
        System.out.println(s);
        return s;
    }
}
