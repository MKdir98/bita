package ir.bita.system.support.ws;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;

/**
 * Third-party SOAP service for WS-Security integration tests.
 * Accepts a message and a delay time, waits time seconds, returns "I get" + message.
 */
@WebService(targetNamespace = "http://ws.esb.bita.ir/", name = "EchoService")
public interface EchoService {

    @WebMethod(operationName = "echo")
    @WebResult(name = "echoResponse")
    String echo(@WebParam(name = "message") String message, @WebParam(name = "time") int time);
}
