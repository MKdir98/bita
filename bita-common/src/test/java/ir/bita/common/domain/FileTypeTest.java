package ir.bita.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FileType Enum")
class FileTypeTest {

    @ParameterizedTest
    @DisplayName("should detect file type from filename")
    @CsvSource({
            "service.wsdl, WSDL",
            "schema.xsd, XSD",
            "config.properties, PROPERTIES",
            "Processor.java, JAVA",
            "library.jar, JAR",
            "unknown.txt, OTHER",
            "SERVICE.WSDL, WSDL",
            "Schema.XSD, XSD"
    })
    void shouldDetectFileTypeFromFilename(String filename, FileType expected) {
        assertThat(FileType.fromFilename(filename)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("should return OTHER for null or blank filename")
    @NullAndEmptySource
    void shouldReturnOtherForInvalidFilename(String filename) {
        assertThat(FileType.fromFilename(filename)).isEqualTo(FileType.OTHER);
    }

    @Test
    @DisplayName("WSDL and XSD should be XML-based")
    void wsdlAndXsdShouldBeXmlBased() {
        assertThat(FileType.WSDL.isXmlBased()).isTrue();
        assertThat(FileType.XSD.isXmlBased()).isTrue();
        assertThat(FileType.JAVA.isXmlBased()).isFalse();
    }

    @Test
    @DisplayName("JAVA and JAR should be Java-related")
    void javaAndJarShouldBeJavaRelated() {
        assertThat(FileType.JAVA.isJavaRelated()).isTrue();
        assertThat(FileType.JAR.isJavaRelated()).isTrue();
        assertThat(FileType.WSDL.isJavaRelated()).isFalse();
    }

    @Test
    @DisplayName("should have correct MIME types")
    void shouldHaveCorrectMimeTypes() {
        assertThat(FileType.WSDL.getMimeType()).isEqualTo("application/wsdl+xml");
        assertThat(FileType.JAR.getMimeType()).isEqualTo("application/java-archive");
    }
}
