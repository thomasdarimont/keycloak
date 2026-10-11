package org.keycloak.scim.provisioning.hash;

import java.nio.charset.StandardCharsets;

import org.keycloak.scim.provisioning.mapping.Mapping;
import org.keycloak.scim.provisioning.mapping.TestSubject;
import org.keycloak.scim.provisioning.mapping.UserResourceBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CanonicalJsonTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** RFC 8785 §3.2.3: sorting compares UTF-16 code units. */
    @Test
    public void rfc8785SortingVector() throws Exception {
        JsonNode in = JSON.readTree(
                "{\"\\u20ac\":\"Euro Sign\",\"\\r\":\"Carriage Return\",\"\\ufb33\":\"Hebrew Letter Dalet With Dagesh\","
                        + "\"1\":\"One\",\"\\ud83d\\ude00\":\"Emoji: Grinning Face\",\"\\u0080\":\"Control\","
                        + "\"\\u00f6\":\"Latin Small Letter O With Diaeresis\"}");
        assertEquals(
                "{\"\\r\":\"Carriage Return\",\"1\":\"One\",\"\u0080\":\"Control\","
                        + "\"\u00f6\":\"Latin Small Letter O With Diaeresis\",\"\u20ac\":\"Euro Sign\","
                        + "\"\ud83d\ude00\":\"Emoji: Grinning Face\",\"\ufb33\":\"Hebrew Letter Dalet With Dagesh\"}",
                CanonicalJson.toString(in));
    }

    /** RFC 8785 §3.2.2.2: string escaping. */
    @Test
    public void stringEscaping() {
        String s = "\u20ac$\u000f\nA'B\"\\\\\"/";
        assertEquals("\"\u20ac$\\u000f\\nA'B\\\"\\\\\\\\\\\"/\"",
                CanonicalJson.toString(JsonNodeFactory.instance.textNode(s)));
        assertEquals("\"\\b\\f\\r\\t\\u001f\u007f\"",
                CanonicalJson.toString(JsonNodeFactory.instance.textNode("\b\f\r\t\u001f\u007f")));
    }

    /** RFC 8785 §3.2.4 example input, whitespace and order removed. */
    @Test
    public void rfc8785Example() throws Exception {
        JsonNode in = JSON.readTree(
                "{\n  \"numbers\": [333333333.33333329, 1E30, 4.50,\n  2e-3, 0.000000000000000000000000001],\n"
                        + "  \"string\": \"\\u20ac$\\u000F\\u000aA'\\u0042\\u0022\\u005c\\\\\\\"\\/\",\n"
                        + "  \"literals\": [null, true, false]\n}");
        assertEquals(
                "{\"literals\":[null,true,false],\"numbers\":[333333333.3333333,1e+30,4.5,0.002,1e-27],"
                        + "\"string\":\"\u20ac$\\u000f\\nA'B\\\"\\\\\\\\\\\"/\"}",
                CanonicalJson.toString(in));
    }

    /** RFC 8785 Appendix B: IEEE 754 bit patterns and their ECMAScript serialisation. */
    @ParameterizedTest
    @CsvSource({
            "0000000000000000, 0",
            "8000000000000000, 0",
            "0000000000000001, 5e-324",
            "8000000000000001, -5e-324",
            "7fefffffffffffff, 1.7976931348623157e+308",
            "ffefffffffffffff, -1.7976931348623157e+308",
            "4340000000000000, 9007199254740992",
            "c340000000000000, -9007199254740992",
            "4430000000000000, 295147905179352830000",
            "44b52d02c7e14af5, 9.999999999999997e+22",
            "44b52d02c7e14af6, 1e+23",
            "44b52d02c7e14af7, 1.0000000000000001e+23",
            "444b1ae4d6e2ef4e, 999999999999999700000",
            "444b1ae4d6e2ef4f, 999999999999999900000",
            "444b1ae4d6e2ef50, 1e+21",
            "3eb0c6f7a0b5ed8c, 9.999999999999997e-7",
            "3eb0c6f7a0b5ed8d, 0.000001",
            "41b3de4355555553, 333333333.3333332",
            "41b3de4355555554, 333333333.33333325",
            "41b3de4355555555, 333333333.3333333",
            "41b3de4355555556, 333333333.3333334",
            "41b3de4355555557, 333333333.33333343",
            "becbf647612f3696, -0.0000033333333333333333",
            "43143ff3c1cb0959, 1424953923781206.2"
    })
    public void rfc8785NumberVectors(String bits, String expected) {
        double d = Double.longBitsToDouble(Long.parseUnsignedLong(bits, 16));
        assertEquals(expected, CanonicalJson.number(d));
    }

    @Test
    public void nanIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CanonicalJson.number(Double.NaN));
    }

    @Test
    public void keyOrderIndependence() {
        ObjectNode a = JsonNodeFactory.instance.objectNode();
        a.put("b", 1).put("a", "x").putObject("c").put("z", true).put("y", false);
        ObjectNode b = JsonNodeFactory.instance.objectNode();
        ObjectNode c = b.putObject("c");
        c.put("y", false).put("z", true);
        b.put("a", "x").put("b", 1);
        assertArrayEquals(CanonicalJson.canonicalize(a), CanonicalJson.canonicalize(b));
        assertEquals("{\"a\":\"x\",\"b\":1,\"c\":{\"y\":false,\"z\":true}}",
                new String(CanonicalJson.canonicalize(a), StandardCharsets.UTF_8));
    }

    @Test
    public void unicodeIsWrittenAsUtf8() {
        byte[] out = CanonicalJson.canonicalize(JsonNodeFactory.instance.textNode("Zoë 😀"));
        assertArrayEquals("\"Zoë 😀\"".getBytes(StandardCharsets.UTF_8), out);
    }

    @Test
    public void loneSurrogateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalJson.canonicalize(JsonNodeFactory.instance.textNode("x\ud800")));
    }

    @Test
    public void arraysKeepTheirOrder() throws Exception {
        assertEquals("[3,1,2]", CanonicalJson.toString(JSON.readTree("[3, 1, 2]")));
    }

    @Test
    public void hashOfBuilderOutputEqualsHashOfSameTreeBuiltInAnotherOrder() throws Exception {
        TestSubject s = TestSubject.of("42", "zoe", "zoe@example.com", "Zoë", "Ng");
        ObjectNode built = UserResourceBuilder.build(s, Mapping.defaults(), true);
        JsonNode handMade = JSON.readTree(
                "{\"active\":true,\"emails\":[{\"primary\":true,\"type\":\"work\",\"value\":\"zoe@example.com\"}],"
                        + "\"name\":{\"givenName\":\"Zoë\",\"familyName\":\"Ng\"},\"displayName\":\"Zoë Ng\","
                        + "\"userName\":\"zoe\",\"externalId\":\"42\","
                        + "\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\"]}");
        assertEquals(Hashing.hash(handMade), Hashing.hash(built));
        assertThat(Hashing.hash(built), matchesPattern("[0-9a-f]{64}"));
    }

    @Test
    public void sha256KnownAnswer() {
        // FIPS 180-2 test vector "abc"
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Hashing.sha256Hex("abc".getBytes(StandardCharsets.US_ASCII)));
    }
}
