package ir.bita.esb.ws;

import jakarta.jws.WebService;

/**
 * Third-party echo service implementation.
 * Waits {@code time} seconds then returns {@code "I get" + message}.
 * Called directly by CXF via JaxWsServerFactoryBean (POJO dispatch — no Camel involvement).
 */
@WebService(endpointInterface = "ir.bita.esb.ws.EchoService",
            targetNamespace   = "http://ws.esb.bita.ir/",
            serviceName       = "EchoService")
public class EchoServiceImpl implements EchoService {

    @Override
    public String echo(String message, int time) {
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
