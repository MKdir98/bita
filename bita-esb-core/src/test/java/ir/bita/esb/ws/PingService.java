package ir.bita.esb.ws;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;

/**
 * Simple SOAP service for WS-Security integration tests.
 */
@WebService(targetNamespace = "http://ws.esb.bita.ir/", name = "PingService")
public interface PingService {

    @WebMethod(operationName = "ping")
    @WebResult(name = "pingResponse")
    String ping(@WebParam(name = "message") String message);
}
