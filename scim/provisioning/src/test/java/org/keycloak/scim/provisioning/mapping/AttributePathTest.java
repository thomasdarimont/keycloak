package org.keycloak.scim.provisioning.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AttributePathTest {

    @Test
    public void simple() {
        AttributePath p = AttributePath.parse("userName");
        assertEquals(new AttributePath(null, "userName", null, null, null), p);
        assertEquals(AttributePath.Shape.SIMPLE, p.shape());
    }

    @Test
    public void subAttribute() {
        AttributePath p = AttributePath.parse("name.givenName");
        assertEquals(new AttributePath(null, "name", "givenName", null, null), p);
        assertEquals(AttributePath.Shape.COMPLEX, p.shape());
    }

    @Test
    public void filteredMultiValued() {
        // RFC 7644 §3.4.2.2 attrExp: attrPath SP compareOp SP compValue
        AttributePath p = AttributePath.parse("emails[type eq \"work\"].value");
        assertEquals(new AttributePath(null, "emails", "value", "type", "work"), p);
        assertEquals(AttributePath.Shape.MULTI_VALUED, p.shape());
        assertEquals("emails[type eq \"work\"].value", p.canonical());
    }

    @Test
    public void filterOperatorIsCaseInsensitiveAndValueIsAJsonString() {
        AttributePath p = AttributePath.parse("phoneNumbers[type EQ \"wo\\\"rk\\u0021\"].value");
        assertEquals("wo\"rk!", p.filterValue());
    }

    @Test
    public void extensionTwoPointZeroForm() {
        // RFC 7644 §3.10: <schema URN>:<attribute>
        AttributePath p = AttributePath.parse("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department");
        assertEquals("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User", p.schemaUrn());
        assertEquals("department", p.attribute());
        assertNull(p.subAttribute());
    }

    @Test
    public void extensionTwoPointZeroFormWithSubAttribute() {
        AttributePath p = AttributePath.parse("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.value");
        assertEquals("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User", p.schemaUrn());
        assertEquals("manager", p.attribute());
        assertEquals("value", p.subAttribute());
    }

    @Test
    public void slackOnePointXDottedFormSplitsVersionIntoSchema() {
        AttributePath p = AttributePath.parse("urn:scim:schemas:extension:enterprise:1.0.department");
        assertEquals("urn:scim:schemas:extension:enterprise:1.0", p.schemaUrn());
        assertEquals("department", p.attribute());
        assertNull(p.subAttribute());
    }

    @Test
    public void onePointXDottedFormWithSubAttribute() {
        AttributePath p = AttributePath.parse("urn:scim:schemas:extension:enterprise:1.0.manager.managerId");
        assertEquals("urn:scim:schemas:extension:enterprise:1.0", p.schemaUrn());
        assertEquals("manager", p.attribute());
        assertEquals("managerId", p.subAttribute());
    }

    @Test
    public void coreSchemaQualifiedPathIsNormalised() {
        assertEquals(AttributePath.parse("userName"),
                AttributePath.parse("urn:ietf:params:scim:schemas:core:2.0:User:userName"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "  ",
            "1abc",
            "user name",
            "name.",
            "name..x",
            ".name",
            "emails[type eq \"work\"]",
            "emails[type ne \"work\"].value",
            "emails[type eq work].value",
            "emails[type eq \"work\".value",
            "emails[type eq \"work\"]x",
            "urn:",
            "urn:x:",
            "urn:scim:schemas:extension:enterprise:1.0",
            "name.given$Name",
            "userName=email"
    })
    public void rejectsGarbage(String text) {
        assertThrows(MappingException.class, () -> AttributePath.parse(text));
    }

    @Test
    public void errorsNameThePosition() {
        MappingException e = assertThrows(MappingException.class,
                () -> AttributePath.parse("emails[type ne \"x\"].value"));
        assertThat(e.getMessage(), containsString("at 13"));
        assertThat(e.getMessage(), containsString("eq"));
    }
}
