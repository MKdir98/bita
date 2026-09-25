package ir.bita.esb.ws;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class Test {
    public static void main(String[] args) throws Exception {

        String test = new WsdlTool().createClientWsdl(Files.readString(Path.of(
                "/home/mehdi/all/repositories/github.com/bita/bita-esb-core/src/test/resources/echo.wsdl"
        )), true);
        System.out.println(test);
    }
}
