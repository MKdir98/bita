package ir.bita.esb.ws;

import org.apache.wss4j.common.ext.WSPasswordCallback;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;

/** Reusable WSS4J password callback handler for tests. */
public class PasswordCallbackHandler implements CallbackHandler {

    private final String password;

    public PasswordCallbackHandler(String password) {
        this.password = password;
    }

    @Override
    public void handle(Callback[] callbacks) {
        for (Callback cb : callbacks)
            if (cb instanceof WSPasswordCallback pc) pc.setPassword(password);
    }
}
